package com.pixelkaart.twinspace;

interface ICloneService {
    boolean isOwner();
    String cloneApp(String packageName);
    String uninstallApp(String packageName);
    boolean setFrozen(String packageName, boolean frozen);
    boolean isFrozen(String packageName);
    List<String> listPackages();
}
