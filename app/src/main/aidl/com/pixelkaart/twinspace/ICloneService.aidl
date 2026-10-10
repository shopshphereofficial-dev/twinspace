package com.pixelkaart.twinspace;

interface ICloneService {
    boolean cloneApp(String packageName);
    boolean uninstallApp(String packageName);
}
