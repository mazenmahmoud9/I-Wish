package iti.iwish.shared;

import java.io.Serializable;

public record WishItem(long id, long ownerId, String ownerName, String title, String note, double price, double funded, String status) implements Serializable {
    private static final long serialVersionUID = 1L;
    public double remaining() { return Math.max(0, price - funded); }
    public double progress() { return price == 0 ? 0 : Math.min(1, funded / price); }
}
