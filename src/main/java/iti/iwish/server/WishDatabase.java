package iti.iwish.server;

import iti.iwish.shared.CatalogItem;
import iti.iwish.shared.Friend;
import iti.iwish.shared.Notice;
import iti.iwish.shared.User;
import iti.iwish.shared.WishItem;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** JDBC repository used exclusively by the server. All SQL is parameterized. */
public final class WishDatabase {
    private static final String URL = "jdbc:h2:" + System.getProperty("user.home") + "/.iwish/iwish-db;MODE=MySQL;AUTO_SERVER=TRUE";

    public WishDatabase() throws SQLException, IOException {
        try (Connection connection = connection()) {
            String sql;
            try (InputStream stream = getClass().getResourceAsStream("/schema.sql")) {
                if (stream == null) throw new IOException("schema.sql is missing");
                sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String statement : sql.split(";\\s*(?:\\r?\\n|$)")) {
                if (!statement.isBlank()) try (Statement s = connection.createStatement()) { s.execute(statement); }
            }
        }
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(URL, "sa", ""); }

    public User register(String name, String email, String password) throws SQLException {
        if (name.isBlank() || !email.contains("@") || password.length() < 4) throw new IllegalArgumentException("Enter a name, a valid email, and a password of 4+ characters.");
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement("INSERT INTO app_user(display_name,email,password_hash) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, name.trim()); p.setString(2, email.trim().toLowerCase()); p.setString(3, hash(password)); p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) { keys.next(); return new User(keys.getLong(1), name.trim(), email.trim().toLowerCase()); }
        } catch (SQLException e) { if ("23505".equals(e.getSQLState())) throw new IllegalArgumentException("That email already has an I-Wish account."); throw e; }
    }

    public User login(String email, String password) throws SQLException {
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement("SELECT id,display_name,email FROM app_user WHERE email=? AND password_hash=?")) {
            p.setString(1, email.trim().toLowerCase()); p.setString(2, hash(password));
            try (ResultSet r = p.executeQuery()) { if (r.next()) return user(r); }
        }
        throw new IllegalArgumentException("Email or password is incorrect.");
    }

    public List<Friend> friends(long userId) throws SQLException {
        String sql = "SELECT f.id, CASE WHEN f.requester_id=? THEN u2.id ELSE u1.id END uid, CASE WHEN f.requester_id=? THEN u2.display_name ELSE u1.display_name END name, CASE WHEN f.requester_id=? THEN u2.email ELSE u1.email END email, f.status, f.addressee_id=? incoming FROM friendship f JOIN app_user u1 ON u1.id=f.requester_id JOIN app_user u2 ON u2.id=f.addressee_id WHERE f.requester_id=? OR f.addressee_id=? ORDER BY f.created_at DESC";
        List<Friend> out = new ArrayList<>();
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i=1;i<=6;i++) p.setLong(i, userId);
            try (ResultSet r=p.executeQuery()) { while(r.next()) out.add(new Friend(r.getLong("id"),r.getLong("uid"),r.getString("name"),r.getString("email"),r.getString("status"),r.getBoolean("incoming"))); }
        } return out;
    }

    public void requestFriend(long from, String email) throws SQLException {
        User target = findUserByEmail(email);
        if (target == null) throw new IllegalArgumentException("No I-Wish user has that email yet.");
        if (target.id() == from) throw new IllegalArgumentException("You cannot add yourself as a friend.");
        try (Connection c=connection(); PreparedStatement p=c.prepareStatement("INSERT INTO friendship(requester_id,addressee_id,status) VALUES(?,?, 'PENDING')")) {
            p.setLong(1,from);p.setLong(2,target.id());p.executeUpdate();
        } catch(SQLException e) { if("23505".equals(e.getSQLState())) throw new IllegalArgumentException("A request already exists between you two."); throw e; }
    }

    public void replyFriend(long userId, long relationshipId, boolean accept) throws SQLException {
        try(Connection c=connection(); PreparedStatement p=c.prepareStatement("UPDATE friendship SET status=? WHERE id=? AND addressee_id=? AND status='PENDING'")) {
            p.setString(1,accept?"ACCEPTED":"DECLINED");p.setLong(2,relationshipId);p.setLong(3,userId); if(p.executeUpdate()==0) throw new IllegalArgumentException("That request is no longer available.");
        }
    }
    public void removeFriend(long userId,long relationshipId) throws SQLException { try(Connection c=connection();PreparedStatement p=c.prepareStatement("DELETE FROM friendship WHERE id=? AND (requester_id=? OR addressee_id=?)")){p.setLong(1,relationshipId);p.setLong(2,userId);p.setLong(3,userId);p.executeUpdate();} }

    public List<WishItem> wishes(long ownerId) throws SQLException { return wishQuery("WHERE w.owner_id=?", ownerId); }
    public List<WishItem> friendWishes(long userId, long friendId) throws SQLException {
        if (!areFriends(userId,friendId)) throw new IllegalArgumentException("You can only view accepted friends' wishes.");
        return wishQuery("WHERE w.owner_id=?",friendId);
    }
    private List<WishItem> wishQuery(String predicate,long id) throws SQLException {
        String sql="SELECT w.id,w.owner_id,u.display_name,w.title,w.note,w.price,w.status,COALESCE(SUM(c.amount),0) funded FROM wish_item w JOIN app_user u ON u.id=w.owner_id LEFT JOIN contribution c ON c.wish_item_id=w.id "+predicate+" GROUP BY w.id,w.owner_id,u.display_name,w.title,w.note,w.price,w.status ORDER BY w.status,w.created_at DESC";
        List<WishItem> out=new ArrayList<>(); try(Connection c=connection();PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(wish(r));}}return out;
    }
    public WishItem addWish(long owner,String title,String note,double price) throws SQLException { if(title.isBlank()||price<=0)throw new IllegalArgumentException("An item title and a positive price are required.");try(Connection c=connection();PreparedStatement p=c.prepareStatement("INSERT INTO wish_item(owner_id,title,note,price) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,owner);p.setString(2,title.trim());p.setString(3,note.trim());p.setBigDecimal(4,BigDecimal.valueOf(price));p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();return getWish(r.getLong(1));}} }
    public void updateWish(long owner,long id,String title,String note,double price) throws SQLException {if(title.isBlank()||price<=0)throw new IllegalArgumentException("An item title and a positive price are required.");try(Connection c=connection();PreparedStatement p=c.prepareStatement("UPDATE wish_item SET title=?,note=?,price=? WHERE id=? AND owner_id=? AND status='OPEN'")){p.setString(1,title.trim());p.setString(2,note.trim());p.setBigDecimal(3,BigDecimal.valueOf(price));p.setLong(4,id);p.setLong(5,owner);if(p.executeUpdate()==0)throw new IllegalArgumentException("Only open items in your list can be changed.");}}
    public void deleteWish(long owner,long id) throws SQLException {try(Connection c=connection();PreparedStatement p=c.prepareStatement("DELETE FROM wish_item WHERE id=? AND owner_id=? AND status='OPEN'")){p.setLong(1,id);p.setLong(2,owner);if(p.executeUpdate()==0)throw new IllegalArgumentException("Only open items in your list can be deleted.");}}

    public void contribute(long buyer,long itemId,double amount) throws SQLException {
        if(amount<=0)throw new IllegalArgumentException("Contribution must be greater than zero.");
        try(Connection c=connection()) { c.setAutoCommit(false); try {
            WishItem wish=getWish(c,itemId); if(wish==null)throw new IllegalArgumentException("This wish item no longer exists."); if(wish.ownerId()==buyer)throw new IllegalArgumentException("You cannot contribute to your own wish."); if(!areFriends(c,buyer,wish.ownerId()))throw new IllegalArgumentException("You may contribute only to an accepted friend's wish."); if(wish.status().equals("FUNDED"))throw new IllegalArgumentException("This item is already fully funded."); if(BigDecimal.valueOf(amount).compareTo(BigDecimal.valueOf(wish.remaining()))>0)throw new IllegalArgumentException("That exceeds the remaining amount: EGP "+String.format("%.2f",wish.remaining()));
            try(PreparedStatement p=c.prepareStatement("INSERT INTO contribution(wish_item_id,buyer_id,amount) VALUES(?,?,?)")){p.setLong(1,itemId);p.setLong(2,buyer);p.setBigDecimal(3,BigDecimal.valueOf(amount));p.executeUpdate();}
            WishItem after=getWish(c,itemId); if(after.remaining()<0.005){
                try(PreparedStatement p=c.prepareStatement("UPDATE wish_item SET status='FUNDED' WHERE id=?")){p.setLong(1,itemId);p.executeUpdate();}
                List<User> contributors = contributors(c, itemId);
                String names = contributors.stream().map(User::name).reduce((a,b)->a+", "+b).orElse("Your friends");
                notice(c,wish.ownerId(),"🎁 "+names+" fully funded your ‘"+wish.title()+"’!");
                for (User contributor : contributors) notice(c,contributor.id(),"✨ You helped complete ‘"+wish.title()+"’ for "+wish.ownerName()+".");
            } c.commit();
        }catch(Exception e){c.rollback();throw e;} }
    }

    public List<Notice> notices(long userId) throws SQLException {List<Notice>out=new ArrayList<>();try(Connection c=connection();PreparedStatement p=c.prepareStatement("SELECT id,message,is_read,created_at FROM notification WHERE recipient_id=? ORDER BY created_at DESC")){p.setLong(1,userId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new Notice(r.getLong(1),r.getString(2),r.getBoolean(3),r.getTimestamp(4).toLocalDateTime()));}}return out;}
    public void markNoticesRead(long userId) throws SQLException {try(Connection c=connection();PreparedStatement p=c.prepareStatement("UPDATE notification SET is_read=TRUE WHERE recipient_id=?")){p.setLong(1,userId);p.executeUpdate();}}
    public List<CatalogItem> catalog() throws SQLException {List<CatalogItem>out=new ArrayList<>();try(Connection c=connection();PreparedStatement p=c.prepareStatement("SELECT id,name,category,suggested_price FROM catalog_item ORDER BY category,name");ResultSet r=p.executeQuery()){while(r.next())out.add(new CatalogItem(r.getLong(1),r.getString(2),r.getString(3),r.getDouble(4)));}return out;}

    private User findUserByEmail(String email)throws SQLException{try(Connection c=connection();PreparedStatement p=c.prepareStatement("SELECT id,display_name,email FROM app_user WHERE email=?")){p.setString(1,email.trim().toLowerCase());try(ResultSet r=p.executeQuery()){return r.next()?user(r):null;}}}
    private User findUser(Connection c,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT id,display_name,email FROM app_user WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){r.next();return user(r);}}}
    private WishItem getWish(long id)throws SQLException{try(Connection c=connection()){return getWish(c,id);}}
    private WishItem getWish(Connection c,long id)throws SQLException{String sql="SELECT w.id,w.owner_id,u.display_name,w.title,w.note,w.price,w.status,COALESCE(SUM(x.amount),0) funded FROM wish_item w JOIN app_user u ON u.id=w.owner_id LEFT JOIN contribution x ON x.wish_item_id=w.id WHERE w.id=? GROUP BY w.id,w.owner_id,u.display_name,w.title,w.note,w.price,w.status";try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){return r.next()?wish(r):null;}}}
    private boolean areFriends(long a,long b)throws SQLException{try(Connection c=connection()){return areFriends(c,a,b);}}
    private boolean areFriends(Connection c,long a,long b)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM friendship WHERE status='ACCEPTED' AND ((requester_id=? AND addressee_id=?) OR (requester_id=? AND addressee_id=?))")){p.setLong(1,a);p.setLong(2,b);p.setLong(3,b);p.setLong(4,a);try(ResultSet r=p.executeQuery()){return r.next();}}}
    private List<User> contributors(Connection c, long itemId) throws SQLException { List<User> users=new ArrayList<>(); try(PreparedStatement p=c.prepareStatement("SELECT DISTINCT u.id,u.display_name,u.email FROM contribution x JOIN app_user u ON u.id=x.buyer_id WHERE x.wish_item_id=?")){p.setLong(1,itemId);try(ResultSet r=p.executeQuery()){while(r.next())users.add(user(r));}} return users; }
    private void notice(Connection c,long recipient,String message)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO notification(recipient_id,message) VALUES(?,?)")){p.setLong(1,recipient);p.setString(2,message);p.executeUpdate();}}
    private static User user(ResultSet r)throws SQLException{return new User(r.getLong(1),r.getString(2),r.getString(3));}
    private static WishItem wish(ResultSet r)throws SQLException{return new WishItem(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),r.getDouble(6),r.getDouble(8),r.getString(7));}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
