// FILE: CodexMobileAppDelegate.swift
// Purpose: Bridges APNs registration callbacks into the service layer without coupling SwiftUI views to UIApplicationDelegate.
// Layer: App
// Exports: CodexMobileAppDelegate, Notification.Name push-registration helpers
// Depends on: Foundation, UIKit

import Foundation
import UIKit

extension Notification.Name {
    static let codexDidRegisterForRemoteNotifications = Notification.Name("codex.didRegisterForRemoteNotifications")
    static let codexDidFailToRegisterForRemoteNotifications = Notification.Name("codex.didFailToRegisterForRemoteNotifications")
}

final class CodexMobileAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        LookInsideBootstrap.startIfEnabled()
        return true
    }

    // Forwards the APNs token so CodexService can persist and sync it to the paired Mac bridge.
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        NotificationCenter.default.post(
            name: .codexDidRegisterForRemoteNotifications,
            object: nil,
            userInfo: [
                "deviceToken": deviceToken,
            ]
        )
    }

    // Keeps registration failures observable in debug builds without surfacing noisy UI errors.
    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        NotificationCenter.default.post(
            name: .codexDidFailToRegisterForRemoteNotifications,
            object: nil,
            userInfo: [
                "error": error,
            ]
        )
    }
}
