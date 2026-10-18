package com.cubeworks.hr.asset;

/**
 * Category of a physical asset in the asset register.
 *
 * <p>Stored as {@code varchar(20)} with a CHECK constraint in {@code hr.asset.asset_type}.</p>
 */
public enum AssetType {
    LAPTOP,
    MONITOR,
    PHONE,
    ACCESS_BADGE,
    DOCKING_STATION,
    HEADSET,
    KEYBOARD_MOUSE,
    OTHER
}
