package com.hikari0422.sshsend;

import com.hikari0422.sshsend.ssh.ConnectionManager;
import com.hikari0422.sshsend.ssh.SFTPService;
import com.hikari0422.sshsend.ssh.SSHConnect;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import net.schmizz.sshj.sftp.RemoteResourceInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class Controller {
    @FXML private Pane loginPage;
    @FXML private Pane browserPage;
    @FXML private ComboBox<ConnectionProfile> savedConnections;
    @FXML private Button deleteConnectionButton;
    @FXML private Label loginStatusLabel;
    @FXML private TextField host;
    @FXML private TextField username;
    @FXML private TextField passwd;
    @FXML private TextField port;
    @FXML private Button connectButton;
    @FXML private TextField localFile;
    @FXML private TextField remotePath;
    @FXML private Button uploadButton;
    @FXML private TextField browserPath;
    @FXML private Button refreshButton;
    @FXML private TableView<RemoteEntry> remoteTable;
    @FXML private TableColumn<RemoteEntry, String> nameColumn;
    @FXML private TableColumn<RemoteEntry, String> typeColumn;
    @FXML private TableColumn<RemoteEntry, String> sizeColumn;
    @FXML private TableColumn<RemoteEntry, String> modifiedColumn;
    @FXML private Label previewTitle;
    @FXML private Label previewDetails;
    @FXML private TextArea previewContent;
    @FXML private ListView<RemoteEntry> downloadList;
    @FXML private Button addButton;
    @FXML private Button downloadButton;
    @FXML private Label statusLabel;

    private final ObservableList<RemoteEntry> queuedDownloads = FXCollections.observableArrayList();
    private final ObservableList<ConnectionProfile> connectionProfiles = FXCollections.observableArrayList();
    private final Preferences preferences = Preferences.userNodeForPackage(Controller.class).node("connections");
    private ConnectionManager connectionManager;
    private Task<?> activeTask;
    private Task<?> previewTask;
    private static final int PREVIEW_LIMIT = 128 * 1024;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @FXML
    private void initialize() {
        browserPage.setVisible(false);
        browserPage.setManaged(false);
        savedConnections.setItems(connectionProfiles);
        savedConnections.valueProperty().addListener((observable, oldProfile, profile) -> {
            deleteConnectionButton.setDisable(profile == null);
            if (profile != null) fillConnection(profile);
        });
        loadConnectionProfiles();
        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().name()));
        typeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().directory() ? "Folder" : "File"));
        sizeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().formattedSize()));
        modifiedColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().formattedModifiedTime()));
        remoteTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        downloadList.setItems(queuedDownloads);
        downloadList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        remoteTable.getSelectionModel().selectedItemProperty().addListener((observable, oldEntry, entry) -> preview(entry));
        remoteTable.setRowFactory(table -> {
            TableRow<RemoteEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                        && !row.isEmpty() && row.getItem().directory()) {
                    loadDirectory(row.getItem().path());
                }
            });
            return row;
        });
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @FXML
    private void initConnect(ActionEvent ignored) {
        String targetHost = host.getText().trim();
        String targetUsername = username.getText().trim();
        String password = passwd.getText();
        final int targetPort;
        try {
            targetPort = parsePort(port.getText());
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid input", e.getMessage());
            return;
        }
        if (targetHost.isBlank() || targetUsername.isBlank() || password.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Invalid input", "Host, user name, and password are required.");
            return;
        }

        setBusy(true, "Connecting...");
        Task<SSHConnect> task = new Task<>() {
            @Override protected SSHConnect call() throws IOException {
                SSHConnect connection = new SSHConnect();
                connection.connect(targetHost, targetPort, targetUsername, password);
                return connection;
            }
        };
        task.setOnSucceeded(event -> {
            try {
                connectionManager.replace(task.getValue());
                queuedDownloads.clear();
                showBrowserPage();
                setBusy(false, "Connected");
                loadDirectory(".");
            } catch (IOException e) {
                setBusy(false, "Connection failed");
                showAlert(Alert.AlertType.ERROR, "Connection failed", e.getMessage());
            }
        });
        task.setOnFailed(event -> taskFailed(task, "Connection failed"));
        start(task, "ssh-connect");
    }

    @FXML
    private void saveConnection(ActionEvent ignored) {
        String targetHost = host.getText().trim();
        String targetUser = username.getText().trim();
        int targetPort;
        try {
            targetPort = parsePort(port.getText());
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.WARNING, "Cannot save connection", e.getMessage());
            return;
        }
        if (targetHost.isBlank() || targetUser.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Cannot save connection", "Host and user name are required.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(targetHost);
        dialog.setTitle("Save connection");
        dialog.setHeaderText("Save as a frequently used device");
        dialog.setContentText("Device name:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().trim().isEmpty()) return;

        ConnectionProfile selected = savedConnections.getValue();
        String id = selected == null ? UUID.randomUUID().toString() : selected.id();
        Preferences node = preferences.node(id);
        node.put("name", result.get().trim());
        node.put("host", targetHost);
        node.put("username", targetUser);
        node.putInt("port", targetPort);
        loadConnectionProfiles();
        connectionProfiles.stream().filter(profile -> profile.id().equals(id)).findFirst().ifPresent(savedConnections::setValue);
        loginStatusLabel.setText("Connection saved (password is not stored)");
    }

    @FXML
    private void deleteConnection(ActionEvent ignored) {
        ConnectionProfile selected = savedConnections.getValue();
        if (selected == null) return;
        try {
            preferences.node(selected.id()).removeNode();
            preferences.flush();
            loadConnectionProfiles();
            clearConnectionFields();
            loginStatusLabel.setText("Saved connection removed");
        } catch (BackingStoreException e) {
            showAlert(Alert.AlertType.ERROR, "Cannot remove connection", e.getMessage());
        }
    }

    @FXML
    private void disconnect(ActionEvent ignored) {
        try {
            connectionManager.close();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Disconnect failed", e.getMessage());
        }
        queuedDownloads.clear();
        remoteTable.getItems().clear();
        clearPreview("Select a file to preview its contents");
        browserPath.clear();
        passwd.clear();
        browserPage.setVisible(false);
        browserPage.setManaged(false);
        loginPage.setManaged(true);
        loginPage.setVisible(true);
        loginStatusLabel.setText("Disconnected");
    }

    private void showBrowserPage() {
        loginPage.setVisible(false);
        loginPage.setManaged(false);
        browserPage.setManaged(true);
        browserPage.setVisible(true);
    }

    private void loadConnectionProfiles() {
        connectionProfiles.clear();
        try {
            for (String id : preferences.childrenNames()) {
                Preferences node = preferences.node(id);
                String profileHost = node.get("host", "");
                String profileUser = node.get("username", "");
                if (!profileHost.isBlank() && !profileUser.isBlank()) {
                    connectionProfiles.add(new ConnectionProfile(id, node.get("name", profileHost),
                            profileHost, profileUser, node.getInt("port", 22)));
                }
            }
            connectionProfiles.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        } catch (BackingStoreException e) {
            loginStatusLabel.setText("Could not load saved connections");
        }
        savedConnections.setValue(null);
        deleteConnectionButton.setDisable(true);
    }

    private void fillConnection(ConnectionProfile profile) {
        host.setText(profile.host());
        username.setText(profile.username());
        port.setText(Integer.toString(profile.port()));
        passwd.requestFocus();
    }

    private void clearConnectionFields() {
        savedConnections.setValue(null);
        host.clear();
        username.clear();
        passwd.clear();
        port.clear();
    }

    @FXML private void browseRemote(ActionEvent ignored) { loadDirectory(browserPath.getText()); }
    @FXML private void refreshRemote(ActionEvent ignored) { loadDirectory(browserPath.getText()); }

    @FXML
    private void parentDirectory(ActionEvent ignored) {
        String current = browserPath.getText().trim();
        if (current.isEmpty() || current.equals("/")) return;
        int slash = current.lastIndexOf('/');
        loadDirectory(slash <= 0 ? "/" : current.substring(0, slash));
    }

    private void loadDirectory(String requestedPath) {
        try {
            connectionManager.requireConnection();
        } catch (IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Cannot browse", e.getMessage());
            return;
        }
        String path = requestedPath == null || requestedPath.isBlank() ? "." : requestedPath.trim();
        setBusy(true, "Loading " + path + "...");
        Task<DirectoryResult> task = new Task<>() {
            @Override protected DirectoryResult call() throws IOException {
                try (SFTPService sftp = new SFTPService(connectionManager.requireConnection())) {
                    String canonicalPath = sftp.canonicalize(path);
                    List<RemoteEntry> entries = sftp.list(canonicalPath).stream().map(RemoteEntry::from).toList();
                    return new DirectoryResult(canonicalPath, entries);
                }
            }
        };
        task.setOnSucceeded(event -> {
            DirectoryResult result = task.getValue();
            browserPath.setText(result.path());
            remoteTable.setItems(FXCollections.observableArrayList(result.entries()));
            clearPreview("Select a file to preview its contents");
            setBusy(false, result.entries().size() + " item(s)");
        });
        task.setOnFailed(event -> taskFailed(task, "Cannot open remote folder"));
        start(task, "sftp-list");
    }

    @FXML
    private void addSelected(ActionEvent ignored) {
        int before = queuedDownloads.size();
        remoteTable.getSelectionModel().getSelectedItems().stream()
                .filter(entry -> !entry.directory())
                .forEach(entry -> { if (!queuedDownloads.contains(entry)) queuedDownloads.add(entry); });
        statusLabel.setText((queuedDownloads.size() - before) + " file(s) added; " + queuedDownloads.size() + " queued");
        downloadButton.setDisable(queuedDownloads.isEmpty());
    }

    @FXML
    private void removeSelected(ActionEvent ignored) {
        queuedDownloads.removeAll(new ArrayList<>(downloadList.getSelectionModel().getSelectedItems()));
        downloadButton.setDisable(queuedDownloads.isEmpty());
    }

    @FXML
    private void clearSelection(ActionEvent ignored) {
        queuedDownloads.clear();
        downloadButton.setDisable(true);
    }

    @FXML
    private void downloadSelected(ActionEvent ignored) {
        if (queuedDownloads.isEmpty()) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose download folder");
        File destination = chooser.showDialog(downloadButton.getScene().getWindow());
        if (destination == null) return;

        List<RemoteEntry> files = List.copyOf(queuedDownloads);
        Path destinationDirectory = destination.toPath();
        setBusy(true, "Downloading 0 / " + files.size());
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws IOException {
                Files.createDirectories(destinationDirectory);
                try (SFTPService sftp = new SFTPService(connectionManager.requireConnection())) {
                    for (int i = 0; i < files.size() && !isCancelled(); i++) {
                        RemoteEntry entry = files.get(i);
                        sftp.download(entry.path(), uniqueTarget(destinationDirectory, entry.name()));
                        int completed = i + 1;
                        Platform.runLater(() -> statusLabel.setText("Downloading " + completed + " / " + files.size()));
                    }
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            queuedDownloads.removeAll(files);
            setBusy(false, "Downloaded " + files.size() + " file(s) to " + destinationDirectory);
            showAlert(Alert.AlertType.INFORMATION, "Download complete", "Downloaded " + files.size() + " file(s) to:\n" + destinationDirectory);
        });
        task.setOnFailed(event -> taskFailed(task, "Download failed"));
        start(task, "sftp-download");
    }

    private static Path uniqueTarget(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) return candidate;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int index = 1; ; index++) {
            candidate = directory.resolve(base + " (" + index + ")" + extension);
            if (!Files.exists(candidate)) return candidate;
        }
    }

    @FXML
    private void chooseFile(ActionEvent ignored) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to upload");
        File selectedFile = chooser.showOpenDialog(localFile.getScene().getWindow());
        if (selectedFile != null) {
            localFile.setText(selectedFile.getAbsolutePath());
            if (remotePath.getText().isBlank()) remotePath.setText(selectedFile.getName());
        }
    }

    @FXML
    private void upload(ActionEvent ignored) {
        final Path source;
        try {
            source = Path.of(localFile.getText().trim());
            if (!Files.isRegularFile(source)) throw new IllegalArgumentException("Select an existing local file.");
            if (remotePath.getText().isBlank()) throw new IllegalArgumentException("Remote path is required.");
            connectionManager.requireConnection();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Cannot upload", e.getMessage());
            return;
        }
        String destination = remotePath.getText().trim();
        setBusy(true, "Uploading...");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws IOException {
                try (SFTPService sftp = new SFTPService(connectionManager.requireConnection())) {
                    sftp.upload(source, destination);
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false, "Upload complete");
            showAlert(Alert.AlertType.INFORMATION, "Upload complete", "File uploaded to " + destination);
            loadDirectory(browserPath.getText());
        });
        task.setOnFailed(event -> taskFailed(task, "Upload failed"));
        start(task, "sftp-upload");
    }

    public void close() {
        if (activeTask != null) activeTask.cancel(true);
        if (previewTask != null) previewTask.cancel(true);
    }

    private void preview(RemoteEntry entry) {
        if (previewTask != null) previewTask.cancel(true);
        if (entry == null) {
            clearPreview("Select a file to preview its contents");
            return;
        }
        previewTitle.setText(entry.name());
        previewDetails.setText(entry.directory() ? "Folder" : entry.formattedSize() + "  •  " + entry.formattedModifiedTime());
        if (entry.directory()) {
            previewContent.setText("Folders cannot be previewed. Double-click the folder to open it.");
            return;
        }
        previewContent.setText("Loading preview...");
        Task<PreviewResult> task = new Task<>() {
            @Override protected PreviewResult call() throws IOException {
                try (SFTPService sftp = new SFTPService(connectionManager.requireConnection())) {
                    byte[] bytes = sftp.readPreview(entry.path(), PREVIEW_LIMIT);
                    if (looksBinary(bytes)) return new PreviewResult(null, bytes.length >= PREVIEW_LIMIT);
                    return new PreviewResult(new String(bytes, StandardCharsets.UTF_8), bytes.length >= PREVIEW_LIMIT);
                }
            }
        };
        previewTask = task;
        task.setOnSucceeded(event -> {
            if (previewTask != task) return;
            PreviewResult result = task.getValue();
            if (result.text() == null) {
                previewContent.setText("Binary file preview is not available.");
            } else {
                previewContent.setText(result.text() + (result.truncated() ? "\n\n— Preview limited to 128 KB —" : ""));
                previewContent.positionCaret(0);
            }
        });
        task.setOnFailed(event -> {
            if (previewTask == task) previewContent.setText("Unable to preview this file: " + task.getException().getMessage());
        });
        startPreview(task);
    }

    private void startPreview(Task<?> task) {
        Thread thread = new Thread(task, "sftp-preview");
        thread.setDaemon(true);
        thread.start();
    }

    private void clearPreview(String message) {
        previewTitle.setText("Preview");
        previewDetails.setText("");
        previewContent.setText(message);
    }

    private static boolean looksBinary(byte[] bytes) {
        if (bytes.length == 0) return false;
        int controls = 0;
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned == 0) return true;
            if (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t' && unsigned != '\f') controls++;
        }
        return controls > bytes.length / 20;
    }

    private void start(Task<?> task, String threadName) {
        activeTask = task;
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void taskFailed(Task<?> task, String title) {
        Throwable error = task.getException();
        setBusy(false, title);
        showAlert(Alert.AlertType.ERROR, title, error == null ? "Unknown error." : error.getMessage());
    }

    private void setBusy(boolean busy, String status) {
        boolean connected = connectionManager != null && connectionManager.isConnected();
        connectButton.setDisable(busy);
        refreshButton.setDisable(busy || !connected);
        addButton.setDisable(busy || !connected);
        uploadButton.setDisable(busy || !connected);
        downloadButton.setDisable(busy || queuedDownloads.isEmpty());
        statusLabel.setText(status);
        loginStatusLabel.setText(status);
    }

    private int parsePort(String value) {
        if (value == null || value.isBlank()) return 22;
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1 || parsed > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535.");
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be a number.", e);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "No details available." : message);
        alert.showAndWait();
    }

    public record RemoteEntry(String name, String path, boolean directory, long size, long modifiedTime) {
        static RemoteEntry from(RemoteResourceInfo info) {
            return new RemoteEntry(info.getName(), info.getPath(), info.isDirectory(),
                    info.getAttributes().getSize(), info.getAttributes().getMtime());
        }

        String formattedModifiedTime() {
            return modifiedTime <= 0 ? "" : DATE_FORMAT.format(Instant.ofEpochSecond(modifiedTime));
        }

        String formattedSize() {
            if (directory) return "";
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            if (size < 1024L * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }

        @Override public String toString() { return name + "  —  " + path; }
    }

    private record DirectoryResult(String path, List<RemoteEntry> entries) {}
    private record PreviewResult(String text, boolean truncated) {}

    public record ConnectionProfile(String id, String name, String host, String username, int port) {
        @Override public String toString() { return name + " — " + username + "@" + host; }
    }
}
