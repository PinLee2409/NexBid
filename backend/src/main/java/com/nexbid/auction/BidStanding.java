package com.nexbid.auction;

/**
 * EN: Where one bidder stands on a lot: leading or not while it runs, won or lost once it closes.
 * VI: Vị thế của một người trả giá trên một lô: đang dẫn hay không khi lô còn chạy, thắng hay thua khi đã đóng.
 */
public enum BidStanding {
    WINNING,
    OUTBID,
    WON,
    LOST
}
