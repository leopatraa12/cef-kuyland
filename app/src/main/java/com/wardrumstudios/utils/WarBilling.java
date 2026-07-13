package com.wardrumstudios.utils;

public class WarBilling extends WarBase {
    public native void changeConnection(boolean connected);

    public native void notifyChange(String sku, int state);

    public void AddSKU(String id) {
    }

    public boolean InitBilling() {
        return true;
    }

    public boolean RequestPurchase(String id) {
        return false;
    }

    public String LocalizedPrice(String id) {
        return "";
    }

    public void SetBillingKey(String key) {
    }
}
