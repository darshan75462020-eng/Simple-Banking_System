import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.collections.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.sql.*;

public class SimpleBankingApp extends Application {
    private static final String DB_URL = "jdbc:mysql://127.0.0.1:33060/bank";
    private static final String USER = "root";
    private static final String PASS = "System";
    private int accountId;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        showLogin(stage);
    }

    private void showLogin(Stage stage) {
        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Label message = new Label();
        Button login = new Button("Login");
        Button register = new Button("Register");

        login.setOnAction(e -> {
            int id = login(username.getText(), password.getText());
            if (id > 0) {
                accountId = id;
                showDashboard(stage);
            } else {
                message.setText("Login failed.");
            }
        });

        register.setOnAction(e -> {
            boolean success = register(username.getText(), password.getText());
            message.setText(success ? "Registered successfully." : "Registration failed.");
        });

        VBox layout = new VBox(10, username, password, new HBox(10, login, register), message);
        stage.setScene(new Scene(layout, 300, 150));
        stage.setTitle("Bank Login");
        stage.show();
    }

    private void showDashboard(Stage stage) {
        Label balanceLabel = new Label("Balance: $" + getBalance(accountId));
        TextField amountField = new TextField();
        amountField.setPromptText("Amount");
        Button deposit = new Button("Deposit");
        Button withdraw = new Button("Withdraw");
        Button history = new Button("History");

        deposit.setOnAction(e -> {
            if (update(accountId, amountField.getText(), "DEPOSIT"))
                balanceLabel.setText("Balance: $" + getBalance(accountId));
        });

        withdraw.setOnAction(e -> {
            if (update(accountId, amountField.getText(), "WITHDRAW"))
                balanceLabel.setText("Balance: $" + getBalance(accountId));
        });

        history.setOnAction(e -> showHistory());

        VBox layout = new VBox(10, balanceLabel, new HBox(10, amountField, deposit, withdraw, history));
        stage.setScene(new Scene(layout, 450, 150));
        stage.setTitle("Dashboard");
    }

    private void showHistory() {
        Stage historyStage = new Stage();
        TableView<Transaction> table = new TableView<>();

        TableColumn<Transaction, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));

        TableColumn<Transaction, Double> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(new PropertyValueFactory<>("amount"));

        table.getColumns().addAll(dateCol, typeCol, amtCol);

        ObservableList<Transaction> data = FXCollections.observableArrayList();
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT type, amount, tx_date FROM transactions WHERE account_id = ? ORDER BY tx_date DESC")) {
            stmt.setInt(1, accountId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                data.add(new Transaction(rs.getString("type"), rs.getDouble("amount"), rs.getTimestamp("tx_date").toString()));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        table.setItems(data);
        historyStage.setScene(new Scene(new VBox(table), 400, 300));
        historyStage.setTitle("Transaction History");
        historyStage.show();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private boolean register(String username, String password) {
        String sql = "INSERT INTO accounts(username, password, balance) VALUES (?, ?, 0)";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private int login(String username, String password) {
        String sql = "SELECT id FROM accounts WHERE username = ? AND password = ?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    private double getBalance(int id) {
        String sql = "SELECT balance FROM accounts WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getDouble("balance") : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    private boolean update(int id, String amountStr, String type) {
        try {
            double amount = Double.parseDouble(amountStr);
            String updateSQL = "UPDATE accounts SET balance = balance " + ("DEPOSIT".equals(type) ? "+" : "-") + " ? WHERE id = ? AND (balance >= ? OR ? = 'DEPOSIT')";
            String insertSQL = "INSERT INTO transactions(account_id, type, amount) VALUES (?, ?, ?)";

            try (Connection conn = connect();
                 PreparedStatement updateStmt = conn.prepareStatement(updateSQL);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                updateStmt.setDouble(1, amount);
                updateStmt.setInt(2, id);
                updateStmt.setDouble(3, amount);
                updateStmt.setString(4, type);

                if (updateStmt.executeUpdate() > 0) {
                    insertStmt.setInt(1, id);
                    insertStmt.setString(2, type);
                    insertStmt.setDouble(3, amount);
                    insertStmt.executeUpdate();
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class Transaction {
        private final String type, date;
        private final Double amount;

        public Transaction(String type, Double amount, String date) {
            this.type = type;
            this.amount = amount;
            this.date = date;
        }

        public String getType() {
            return type;
        }

        public Double getAmount() {
            return amount;
        }

        public String getDate() {
            return date;
        }
    }
}
