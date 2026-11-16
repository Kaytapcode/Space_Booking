package com.spacebooking.booking.domain;

public enum BookingAction {
    APPROVE("approve"), REJECT("reject"), CANCEL("cancel"), EXPIRE("expire"), CHECK_IN("check in"),
    MARK_NO_SHOW("mark as no-show"), CHECKOUT("check out"), COMPLETE("complete");

    private final String verb;

    BookingAction(String verb) {
        this.verb = verb;
    }

    /** Wording used in the INVALID_STATE_TRANSITION detail (openapi examples). */
    public String verb() {
        return verb;
    }
}
