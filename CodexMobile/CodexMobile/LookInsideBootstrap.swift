// FILE: LookInsideBootstrap.swift
// Purpose: Starts the LookInside runtime inspector in debug builds for local UI hierarchy inspection.
// Layer: App
// Exports: LookInsideBootstrap
// Depends on: Foundation, LookinServer (debug only)

import Foundation

#if DEBUG && canImport(LookinServer)
import LookinServer

@_silgen_name("LookinServerStart")
private func LookinServerStartBridge()
#endif

enum LookInsideBootstrap {
    static func startIfEnabled(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: [String: String] = ProcessInfo.processInfo.environment
    ) {
#if DEBUG && canImport(LookinServer)
        guard !arguments.contains("--disable-lookinside") else {
            return
        }
        guard environment["REMODEX_DISABLE_LOOKINSIDE"] != "1" else {
            return
        }
        LookinServerStartBridge()
#else
        _ = arguments
        _ = environment
#endif
    }
}
