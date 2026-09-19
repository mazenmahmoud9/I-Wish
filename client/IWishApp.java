package iti.iwish.client;

import iti.iwish.server.WishServer;
import iti.iwish.shared.ApiResponse;
import iti.iwish.shared.CatalogItem;
import iti.iwish.shared.Friend;
import iti.iwish.shared.Notice;
import iti.iwish.shared.User;
import iti.iwish.shared.WishItem;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Polished JavaFX client for the I-Wish gift contribution service. */
public final class IWishApp extends Application {
    private final WishClient client = new WishClient();
    private WishServer embeddedServer;
    private Stage stage;
    private User currentUser;

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("I-Wish · Gifts made together");
        stage.setMinWidth(980); stage.setMinHeight(650);
        showWelcome(); stage.show();
    }
    @Override public void stop() { if (embeddedServer != null) embeddedServer.close(); }

    private void showWelcome() {
        VBox hero = new VBox(14);
        hero.getStyleClass().add("hero"); hero.setPadding(new Insets(48)); hero.setPrefWidth(475); hero.setAlignment(Pos.CENTER_LEFT);
        Label sparkle = new Label("✦  I-WISH"); sparkle.setStyle("-fx-text-fill: #ffcbd7; -fx-font-weight: bold; -fx-font-size: 14px;");
        Label headline = new Label("Make their\nwish come true."); headline.setStyle("-fx-text-fill: white; -fx-font-size: 39px; -fx-font-weight: bold;");
        Label copy = new Label("A warmer way to share wish lists, pitch in together, and celebrate the little things."); copy.setWrapText(true); copy.setStyle("-fx-text-fill: #eee9ff; -fx-font-size: 15px;");
        Label quote = new Label("“Small gifts. Big smiles.”"); quote.setStyle("-fx-text-fill: #ffd8e1; -fx-font-style: italic; -fx-font-size: 16px;");
        hero.getChildren().addAll(sparkle,headline,copy,quote);

        VBox login = loginPane(); login.setMaxWidth(380);
        VBox right = new VBox(16, login); right.setAlignment(Pos.CENTER); right.setPadding(new Insets(38)); HBox.setHgrow(right,Priority.ALWAYS);
        HBox root = new HBox(28,hero,right); root.setPadding(new Insets(30)); root.setAlignment(Pos.CENTER);
        setScene(root, 1020, 670);
    }
    private VBox loginPane() {
        Label heading = new Label("Welcome in"); heading.getStyleClass().add("title");
        Label subtitle = muted("Sign in to see the joy you’re helping create.");
        TextField email = field("Email address"); PasswordField password = password("Password");
        Button signIn = primary("Sign in →"); signIn.setMaxWidth(Double.MAX_VALUE);
        Button register = ghost("Create a new account"); register.setMaxWidth(Double.MAX_VALUE);
        Button startServer = new Button("Start local demo server"); startServer.getStyleClass().add("ghost"); startServer.setMaxWidth(Double.MAX_VALUE);
        signIn.setOnAction(e -> authenticate("LOGIN", email.getText(), password.getText(), ""));
        password.setOnAction(e -> signIn.fire()); register.setOnAction(e -> showRegister()); startServer.setOnAction(e -> startServer());
        VBox card = new VBox(13,heading,subtitle,email,password,signIn,register,new Label("Need a server for the demo?"),startServer);
        card.getStyleClass().add("card"); card.setPadding(new Insets(34)); return card;
    }
    private void showRegister() {
        Label heading = new Label("Start a little magic"); heading.getStyleClass().add("title");
        TextField name=field("Your display name"), email=field("Email address"); PasswordField pass=password("Password (4+ characters)");
        Button join=primary("Create my account ✦"); join.setMaxWidth(Double.MAX_VALUE); Button back=ghost("← Back to sign in"); back.setMaxWidth(Double.MAX_VALUE);
        join.setOnAction(e->authenticate("REGISTER",email.getText(),pass.getText(),name.getText())); back.setOnAction(e->showWelcome());
        VBox card=new VBox(13,heading,muted("Your friends will recognize this name."),name,email,pass,join,back);card.getStyleClass().add("card");card.setPadding(new Insets(34));card.setMaxWidth(410);
        StackPane page=new StackPane(card);page.setPadding(new Insets(30));setScene(page,1020,670);
    }
    private void authenticate(String action,String email,String pass,String name) {
        ApiResponse response=client.call(action,Map.of("email",email,"password",pass,"name",name));
        if(!response.ok()){error(response.message());return;} currentUser=(User)response.payload(); showDashboard("Home");
    }
    private void startServer() {
        if (embeddedServer != null && embeddedServer.isRunning()) { info("The local I-Wish server is already running."); return; }
        try { embeddedServer=new WishServer(); embeddedServer.start(); info("Demo server is ready. You can now create an account."); }
        catch (IOException exception) { info("A server is already listening on port 5217 - you can sign in now."); }
        catch (SQLException exception) { error("Could not initialize the database: "+exception.getMessage()); }
    }

    private void showDashboard(String view) {
        BorderPane root=new BorderPane(); root.setLeft(nav(view)); root.setTop(header(view)); root.setCenter(switch(view){case "My wishes"->wishesPage();case "Friends"->friendsPage();case "Discover"->discoverPage();case "Notifications"->notificationsPage();default->homePage();});
        setScene(root,1180,760);
    }
    private VBox nav(String current) {
        Label logo=new Label("✦  I-WISH");logo.setStyle("-fx-text-fill:white;-fx-font-size:19px;-fx-font-weight:bold;");
        Label caption=new Label("gifts, together");caption.setStyle("-fx-text-fill:#bfb6e8;-fx-font-size:11px;");
        VBox brand=new VBox(2,logo,caption); brand.setPadding(new Insets(4,12,26,12));
        VBox nav=new VBox(7,brand);nav.getStyleClass().add("nav");nav.setPrefWidth(190);
        for(String item:List.of("Home","My wishes","Friends","Discover","Notifications")){Button b=new Button(icon(item)+"  "+item);b.setOnAction(e->showDashboard(item));nav.getChildren().add(b);}
        Region spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS); Button logout=new Button("↩  Sign out");logout.setOnAction(e->{currentUser=null;showWelcome();});nav.getChildren().addAll(spacer,logout);return nav;
    }
    private Node header(String view) { Label page=new Label(view);page.getStyleClass().add("section");Label identity=new Label("Hi, "+currentUser.name()+"  ✦");identity.setStyle("-fx-text-fill:#5a438e;-fx-font-weight:bold;");HBox bar=new HBox(16,page,new Region(),identity);HBox.setHgrow(bar.getChildren().get(1),Priority.ALWAYS);bar.setPadding(new Insets(22,30,18,30));bar.setAlignment(Pos.CENTER_LEFT);return bar; }
    private Node homePage() {
        List<WishItem>wishes=getList("MY_WISHES",Map.of("userId",currentUser.id())); List<Friend>friends=getList("FRIENDS",Map.of("userId",currentUser.id()));
        long funded=wishes.stream().filter(w->w.status().equals("FUNDED")).count(); double raised=wishes.stream().mapToDouble(WishItem::funded).sum();
        HBox stats=new HBox(16, stat("My wishes",String.valueOf(wishes.size()),"little dreams waiting"),stat("Funded",String.valueOf(funded),"ready for happy dances"),stat("Raised",money(raised),"from friends’ kindness"));
        VBox greeting=new VBox(8,new Label("Good things are\non their way."),muted("Keep your wishes fresh, invite your people, and make someone’s day.")); greeting.getStyleClass().add("hero");greeting.setPadding(new Insets(28));((Label)greeting.getChildren().get(0)).setStyle("-fx-text-fill:white;-fx-font-size:27px;-fx-font-weight:bold;");((Label)greeting.getChildren().get(1)).setStyle("-fx-text-fill:#eee9ff;");
        Button add=accent("+ Add a wish");add.setOnAction(e->showDashboard("My wishes")); Button people=ghost("Invite a friend");people.setOnAction(e->showDashboard("Friends")); HBox actions=new HBox(10,add,people);greeting.getChildren().add(actions);
        Label activity=new Label("Your circle");activity.getStyleClass().add("section"); ListView<String>circle=new ListView<>();circle.setPrefHeight(150);circle.setItems(FXCollections.observableArrayList(friends.stream().filter(f->f.status().equals("ACCEPTED")).map(f->"♥  "+f.name()+" is part of your I-Wish circle").toList()));circle.setPlaceholder(muted("Invite a friend to start gifting together."));
        VBox content=new VBox(18,greeting,stats,activity,circle);content.setPadding(new Insets(8,30,30,30));return content;
    }
    private VBox stat(String label,String value,String hint){Label v=new Label(value);v.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#503b8f;");VBox box=new VBox(4,new Label(label),v,muted(hint));box.getStyleClass().add("card");box.setPadding(new Insets(17));box.setPrefWidth(205);return box;}

    private Node wishesPage() {
        TableView<WishItem>table=new TableView<>();table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);column(table,"Wish",WishItem::title);column(table,"Price",w->money(w.price()));progressColumn(table);column(table,"Status",WishItem::status);table.setItems(FXCollections.observableArrayList(getList("MY_WISHES",Map.of("userId",currentUser.id()))));
        TextField title=field("Wish title");TextField price=field("Price in EGP");TextArea note=new TextArea();note.setPromptText("A small note (optional)");note.setPrefRowCount(2);
        ComboBox<CatalogItem>catalog=new ComboBox<>();catalog.setPromptText("Pick from starter catalog");catalog.setMaxWidth(Double.MAX_VALUE);catalog.setItems(FXCollections.observableArrayList(getList("CATALOG",Map.of())));catalog.setOnAction(e->{CatalogItem c=catalog.getValue();if(c!=null){title.setText(c.name());price.setText(String.valueOf(c.price()));}});
        Button save=primary("Add wish");Button update=ghost("Update selected");Button delete=new Button("Delete selected");delete.getStyleClass().add("accent");
        save.setOnAction(e->{ApiResponse r=client.call("ADD_WISH",Map.of("userId",currentUser.id(),"title",title.getText(),"note",note.getText(),"price",parse(price.getText())));if(r.ok()){info(r.message());showDashboard("My wishes");}else error(r.message());});
        update.setOnAction(e->{WishItem w=table.getSelectionModel().getSelectedItem();if(w==null){error("Choose a wish first.");return;}ApiResponse r=client.call("UPDATE_WISH",Map.of("userId",currentUser.id(),"wishId",w.id(),"title",title.getText(),"note",note.getText(),"price",parse(price.getText())));if(r.ok())showDashboard("My wishes");else error(r.message());});
        delete.setOnAction(e->{WishItem w=table.getSelectionModel().getSelectedItem();if(w==null){error("Choose a wish first.");return;}ApiResponse r=client.call("DELETE_WISH",Map.of("userId",currentUser.id(),"wishId",w.id()));if(r.ok())showDashboard("My wishes");else error(r.message());});
        table.getSelectionModel().selectedItemProperty().addListener((o,a,w)->{if(w!=null){title.setText(w.title());price.setText(String.valueOf(w.price()));note.setText(w.note());}});
        VBox form=new VBox(10,new Label("Make a wish"){ {getStyleClass().add("section");} },muted("Add something meaningful to your list."),catalog,title,price,note,new HBox(8,save,update),delete);form.getStyleClass().add("card");form.setPadding(new Insets(19));form.setPrefWidth(300);
        VBox list=new VBox(10,new Label("My wish list"){ {getStyleClass().add("title");} },muted("Select an open item to edit it. Funded wishes are protected."),table);VBox.setVgrow(table,Priority.ALWAYS);HBox layout=new HBox(20,list,form);HBox.setHgrow(list,Priority.ALWAYS);layout.setPadding(new Insets(8,30,30,30));return layout;
    }

    private Node friendsPage() {
        TableView<Friend> table=new TableView<>();table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);column(table,"Friend",Friend::name);column(table,"Email",Friend::email);column(table,"Status",Friend::status);column(table,"Direction",f->f.incoming()?"They invited you":"You invited them");table.setItems(FXCollections.observableArrayList(getList("FRIENDS",Map.of("userId",currentUser.id()))));
        TextField email=field("Friend’s I-Wish email");Button invite=primary("Send invite");invite.setOnAction(e->{ApiResponse r=client.call("REQUEST_FRIEND",Map.of("userId",currentUser.id(),"email",email.getText()));if(r.ok())showDashboard("Friends");else error(r.message());});Button accept=ghost("Accept selected");Button decline=accent("Decline selected");Button remove=new Button("Remove selected");
        accept.setOnAction(e->friendReply(table,true));decline.setOnAction(e->friendReply(table,false));remove.setOnAction(e->{Friend f=selected(table);if(f!=null){ApiResponse r=client.call("REMOVE_FRIEND",Map.of("userId",currentUser.id(),"relationshipId",f.relationshipId()));if(r.ok())showDashboard("Friends");else error(r.message());}});
        VBox side=new VBox(10,new Label("Grow your circle"){ {getStyleClass().add("section");} },muted("Your friends must have an I-Wish account."),email,invite,new Label("Incoming request?"),accept,decline,new Label("Manage connection"),remove);side.getStyleClass().add("card");side.setPadding(new Insets(19));side.setPrefWidth(280);
        VBox main=new VBox(11,new Label("Friends & requests"){ {getStyleClass().add("title");} },muted("Share the good stuff with people you trust."),table);VBox.setVgrow(table,Priority.ALWAYS);HBox page=new HBox(20,main,side);HBox.setHgrow(main,Priority.ALWAYS);page.setPadding(new Insets(8,30,30,30));return page;
    }
    private void friendReply(TableView<Friend>t,boolean accept){Friend f=selected(t);if(f==null)return;if(!f.incoming()||!f.status().equals("PENDING")){error("Select an incoming pending request.");return;}ApiResponse r=client.call("REPLY_FRIEND",Map.of("userId",currentUser.id(),"relationshipId",f.relationshipId(),"accept",accept));if(r.ok())showDashboard("Friends");else error(r.message());}

    private Node discoverPage() {
        List<Friend> allFriends = this.<Friend>getList("FRIENDS", Map.of("userId", currentUser.id()));
        List<Friend> people=allFriends.stream().filter(f->f.status().equals("ACCEPTED")).toList();ComboBox<Friend> friends=new ComboBox<>(FXCollections.observableArrayList(people));friends.setPromptText("Choose a friend");friends.setMaxWidth(300);FlowPane wishes=new FlowPane(15,15);wishes.setPadding(new Insets(16,0,0,0));
        friends.setOnAction(e->{Friend f=friends.getValue();wishes.getChildren().clear();if(f!=null)for(WishItem w:this.<WishItem>getList("FRIEND_WISHES",Map.of("userId",currentUser.id(),"friendId",f.userId())))wishes.getChildren().add(wishCard(w));});
        VBox page=new VBox(8,new Label("Discover their wishes"){ {getStyleClass().add("title");} },muted("Every contribution, no matter the size, moves a wish closer."),friends,wishes);page.setPadding(new Insets(8,30,30,30));return page;
    }
    private Node wishCard(WishItem w){Label name=new Label(w.title());name.getStyleClass().add("section");Label note=muted(w.note().isBlank()?"A lovely surprise is waiting.":w.note());note.setWrapText(true);ProgressBar progress=new ProgressBar(w.progress());progress.setPrefWidth(230);Label balance=new Label(money(w.funded())+" of "+money(w.price())+" · "+money(w.remaining())+" left");Button contribute=primary(w.status().equals("FUNDED")?"Fully funded ✦":"Contribute");contribute.setDisable(w.status().equals("FUNDED"));contribute.setOnAction(e->contribute(w));VBox card=new VBox(9,name,note,progress,balance,contribute);card.getStyleClass().add("card");card.setPadding(new Insets(18));card.setPrefWidth(270);return card;}
    private void contribute(WishItem w){TextField amount=field("Amount (max "+String.format("%.2f",w.remaining())+")");Alert dialog=new Alert(Alert.AlertType.NONE);dialog.setTitle("Share the joy");dialog.setHeaderText("Contribute to “"+w.title()+"”");dialog.getDialogPane().setContent(new VBox(10,muted("Your name stays part of the thank-you."),amount));dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CANCEL);dialog.getDialogPane().getButtonTypes().add(new javafx.scene.control.ButtonType("Send",javafx.scene.control.ButtonBar.ButtonData.OK_DONE));dialog.showAndWait().ifPresent(b->{if(b.getButtonData().isDefaultButton()){ApiResponse r=client.call("CONTRIBUTE",Map.of("userId",currentUser.id(),"wishId",w.id(),"amount",parse(amount.getText())));if(r.ok()){info(r.message());showDashboard("Discover");}else error(r.message());}});}

    private Node notificationsPage(){List<Notice>notices=getList("NOTICES",Map.of("userId",currentUser.id()));ListView<String>list=new ListView<>();list.setItems(FXCollections.observableArrayList(notices.stream().map(n->(n.read()?"":"●  ")+n.message()+"\n    "+n.createdAt().toLocalDate().toString()).toList()));list.setPlaceholder(muted("No notifications yet - joy is on its way."));Button mark=ghost("Mark all as read");mark.setOnAction(e->{client.call("READ_NOTICES",Map.of("userId",currentUser.id()));showDashboard("Notifications");});VBox page=new VBox(13,new Label("A little inbox of joy"){ {getStyleClass().add("title");} },muted("You’ll hear when a gift reaches its goal."),mark,list);VBox.setVgrow(list,Priority.ALWAYS);page.setPadding(new Insets(8,30,30,30));return page;}

    @SuppressWarnings("unchecked") private <T> List<T> getList(String action,Map<String,Object>data){ApiResponse r=client.call(action,data);if(!r.ok()){error(r.message());return List.of();}return (List<T>)r.payload();}
    private <T> void column(TableView<T>table,String name,java.util.function.Function<T,String>value){TableColumn<T,String>c=new TableColumn<>(name);c.setCellValueFactory(v->new ReadOnlyObjectWrapper<>(value.apply(v.getValue())));table.getColumns().add(c);}
    private void progressColumn(TableView<WishItem> table) {
        TableColumn<WishItem, WishItem> column = new TableColumn<>("Gift progress");
        column.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(WishItem wish, boolean empty) {
                super.updateItem(wish, empty);
                if (empty || wish == null) { setGraphic(null); return; }
                ProgressBar bar = new ProgressBar(wish.progress());
                bar.setPrefWidth(145);
                Label amount = new Label(money(wish.funded()) + " / " + money(wish.price())
                        + "  (" + Math.round(wish.progress() * 100) + "%)");
                amount.getStyleClass().add("muted");
                setGraphic(new VBox(4, bar, amount));
            }
        });
        table.getColumns().add(column);
    }
    private Friend selected(TableView<Friend>t){Friend f=t.getSelectionModel().getSelectedItem();if(f==null)error("Choose a friend or request first.");return f;}
    private static String icon(String item){return switch(item){case "Home"->"⌂";case "My wishes"->"♡";case "Friends"->"♧";case "Discover"->"⌕";default->"✦";};}
    private static double parse(String value){try{return Double.parseDouble(value.trim());}catch(Exception e){return -1;}}
    private static String money(double n){return String.format("EGP %,.0f",n);}
    private static Label muted(String text){Label label=new Label(text);label.getStyleClass().add("muted");return label;}
    private static TextField field(String prompt){TextField f=new TextField();f.setPromptText(prompt);return f;}
    private static PasswordField password(String prompt){PasswordField f=new PasswordField();f.setPromptText(prompt);return f;}
    private static Button primary(String text){Button b=new Button(text);b.getStyleClass().add("primary");return b;}
    private static Button ghost(String text){Button b=new Button(text);b.getStyleClass().add("ghost");return b;}
    private static Button accent(String text){Button b=new Button(text);b.getStyleClass().add("accent");return b;}
    private void setScene(Parent root,double width,double height){Scene scene=new Scene(root,width,height);scene.getStylesheets().add(getClass().getResource("/iwish.css").toExternalForm());stage.setScene(scene);}
    private void error(String text){alert(Alert.AlertType.ERROR,"Almost there",text);}
    private void info(String text){alert(Alert.AlertType.INFORMATION,"I-Wish",text);}
    private static void alert(Alert.AlertType type,String title,String text){Alert a=new Alert(type,text);a.setTitle(title);a.setHeaderText(null);a.showAndWait();}
    public static void main(String[] args){launch(args);}
}
