package org.gotti.wurmonline.clientmods.AutoSort;

import java.io.Serializable;
import java.util.Objects;

public class FilterItem implements Serializable {
    private String itemName;
    private boolean applyWorld;
    private int quantity;
    
    public FilterItem(String itemName, boolean applyWorld, int quantity) {
        this.itemName = itemName;
        this.applyWorld = applyWorld;
        this.quantity = quantity;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public boolean isApplyWorld() {
        return applyWorld;
    }

    public void setApplyWorld(boolean applyWorld) {
        this.applyWorld = applyWorld;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(applyWorld) {
            sb.append("world");
        }
        if(quantity != 100) {
            if(applyWorld)
                sb.append("|");
            sb.append("quantity="+Integer.toString(quantity));
        }
        return "[" + sb.toString() + "] " + itemName;
    }
    
    @Override
    public boolean equals(Object other) {
        if(other instanceof FilterItem) {
            FilterItem otherFilter = (FilterItem)other;
            if(this.itemName == otherFilter.itemName &&
                    this.quantity == otherFilter.quantity &&
                    this.applyWorld == otherFilter.applyWorld)
                return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.itemName);
        hash = 53 * hash + (this.applyWorld ? 1 : 0);
        hash = 53 * hash + this.quantity;
        return hash;
    }
}
