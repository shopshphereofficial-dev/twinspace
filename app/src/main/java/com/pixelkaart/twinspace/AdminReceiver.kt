package com.pixelkaart.twinspace

import android.app.admin.DeviceAdminReceiver

/**
 * Device admin / profile-owner receiver. The base class handles the callbacks we
 * need for provisioning a managed (work) profile.
 */
class AdminReceiver : DeviceAdminReceiver()
