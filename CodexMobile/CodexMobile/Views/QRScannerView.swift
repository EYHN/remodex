// FILE: QRScannerView.swift
// Purpose: AVFoundation camera-based QR scanner for relay session pairing.
// Layer: View
// Exports: QRScannerView
// Depends on: SwiftUI, AVFoundation

import AVFoundation
import SwiftUI

struct QRScannerView: View {
    let onBack: (() -> Void)?
    let onScan: (CodexPairingQRPayload) -> Void

    @State private var scannerError: String?
    @State private var bridgeUpdatePrompt: CodexBridgeUpdatePrompt?
    @State private var didCopyBridgeUpdateCommand = false
    @State private var hasCameraPermission = false
    @State private var isCheckingPermission = true
    @State private var isShowingManualPayloadSheet = false
    @State private var manualPayloadText = ""
    @State private var manualPayloadError: String?

    init(
        initialBridgeUpdatePrompt: CodexBridgeUpdatePrompt? = nil,
        initialHasCameraPermission: Bool = false,
        initialIsCheckingPermission: Bool = true,
        onBack: (() -> Void)? = nil,
        onScan: @escaping (CodexPairingQRPayload) -> Void
    ) {
        self.onBack = onBack
        self.onScan = onScan
        _bridgeUpdatePrompt = State(initialValue: initialBridgeUpdatePrompt)
        _hasCameraPermission = State(initialValue: initialHasCameraPermission)
        _isCheckingPermission = State(initialValue: initialIsCheckingPermission)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if isCheckingPermission {
                ProgressView()
                    .tint(.white)
            } else if let bridgeUpdatePrompt {
                bridgeUpdateView(prompt: bridgeUpdatePrompt)
            } else if hasCameraPermission {
                QRCameraPreview { code, resetScanLock in
                    handleScanResult(code, resetScanLock: resetScanLock)
                }
                .ignoresSafeArea()

                scannerOverlay
            } else {
                cameraPermissionView
            }
        }
        .safeAreaInset(edge: .top) {
            if let onBack {
                HStack {
                    backButton(action: onBack)
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
            }
        }
        .task {
            await checkCameraPermission()
        }
        .alert("Scan Error", isPresented: Binding(
            get: { scannerError != nil },
            set: { if !$0 { scannerError = nil } }
        )) {
            Button("OK", role: .cancel) { scannerError = nil }
        } message: {
            Text(scannerError ?? "Invalid QR code")
        }
        .sheet(isPresented: $isShowingManualPayloadSheet, onDismiss: {
            manualPayloadError = nil
        }) {
            ManualPairingPayloadSheet(
                payloadText: $manualPayloadText,
                errorMessage: $manualPayloadError,
                onPasteFromClipboard: pastePayloadFromClipboard,
                onConnect: submitManualPayload,
                onDismiss: dismissManualPayloadSheet
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    // Blocks repeated scans when the camera spots a bridge QR from an incompatible npm release.
    private func bridgeUpdateView(prompt: CodexBridgeUpdatePrompt) -> some View {
        VStack(alignment: .leading, spacing: 24) {
            Spacer()

            VStack(alignment: .leading, spacing: 12) {
                Text(prompt.title)
                    .font(AppFont.title3(weight: .semibold))
                    .foregroundStyle(.white)

                Text(prompt.message)
                    .font(AppFont.body())
                    .foregroundStyle(.white.opacity(0.82))
            }

            VStack(alignment: .leading, spacing: 14) {
                Text("Do these steps on your Mac")
                    .font(AppFont.caption(weight: .semibold))
                    .foregroundStyle(.white.opacity(0.7))

                bridgeUpdateStep(number: "1", title: "Update Remodex", detail: prompt.command, showsCopyButton: true)
                bridgeUpdateStep(number: "2", title: "Start it again", detail: "Run remodex up")
                bridgeUpdateStep(number: "3", title: "Make a new QR code", detail: "Use the new QR shown in the terminal")
                bridgeUpdateStep(number: "4", title: "Come back here", detail: "Then scan the new QR code from the iPhone")
            }

            Button("I Updated It") {
                bridgeUpdatePrompt = nil
                didCopyBridgeUpdateCommand = false
            }
            .font(AppFont.body(weight: .semibold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .foregroundStyle(.black)
            .background(.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .buttonStyle(.plain)

            Spacer()
        }
        .padding(.horizontal, 24)
    }

    private func bridgeUpdateStep(
        number: String,
        title: String,
        detail: String,
        showsCopyButton: Bool = false
    ) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(number)
                .font(AppFont.caption2(weight: .bold))
                .foregroundStyle(.black)
                .frame(width: 20, height: 20)
                .background(.white, in: Circle())
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 8) {
                Text(title)
                    .font(AppFont.subheadline(weight: .semibold))
                    .foregroundStyle(.white)

                Text(detail)
                    .font(showsCopyButton ? AppFont.mono(.caption) : AppFont.caption())
                    .foregroundStyle(.white.opacity(0.82))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(Color.white.opacity(0.08))
                    )

                if showsCopyButton {
                    Button(didCopyBridgeUpdateCommand ? "Copied" : "Copy Command") {
                        UIPasteboard.general.string = detail
                        HapticFeedback.shared.triggerImpactFeedback(style: .light)
                        withAnimation(.easeInOut(duration: 0.2)) {
                            didCopyBridgeUpdateCommand = true
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                didCopyBridgeUpdateCommand = false
                            }
                        }
                    }
                    .font(AppFont.caption(weight: .semibold))
                    .foregroundStyle(.white)
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // Keeps the first-run scanner escapable without turning reconnect recovery into onboarding.
    private func backButton(action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: "chevron.left")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(Color.white.opacity(0.12), in: Circle())
                .overlay(
                    Circle()
                        .stroke(Color.white.opacity(0.18), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Back")
    }

    private var scannerOverlay: some View {
        VStack(spacing: 24) {
            Spacer()

            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.6), lineWidth: 2)
                .frame(width: 250, height: 250)

            Text("Scan QR code from Remodex CLI")
                .font(AppFont.subheadline(weight: .medium))
                .foregroundStyle(.white)

            manualPayloadButton

            Spacer()
        }
        .padding(.horizontal, 24)
    }

    private var cameraPermissionView: some View {
        VStack(spacing: 20) {
            Image(systemName: "camera.fill")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)

            Text("Camera access needed")
                .font(AppFont.title3(weight: .semibold))
                .foregroundStyle(.white)

            Text("Open Settings and allow camera access to scan the pairing QR code.")
                .font(AppFont.subheadline())
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)

            manualPayloadButton
        }
    }

    private var manualPayloadButton: some View {
        Button("Enter Payload Instead") {
            manualPayloadError = nil
            isShowingManualPayloadSheet = true
        }
        .font(AppFont.body(weight: .semibold))
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .foregroundStyle(.white)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.white.opacity(0.12))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.white.opacity(0.18), lineWidth: 1)
        )
        .buttonStyle(.plain)
        .accessibilityLabel("Enter pairing payload instead")
    }

    // Keeps permission-prompt teardown on the main actor so backing out mid-prompt
    // does not race a stale state write against SwiftUI dismissal.
    @MainActor
    private func checkCameraPermission() async {
        let hasPermission: Bool
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            hasPermission = true
        case .notDetermined:
            hasPermission = await AVCaptureDevice.requestAccess(for: .video)
        default:
            hasPermission = false
        }

        guard !Task.isCancelled else {
            return
        }

        hasCameraPermission = hasPermission
        isCheckingPermission = false
    }

    private func handleScanResult(_ code: String, resetScanLock: @escaping () -> Void) {
        submitPairingCode(code, resetScanLock: resetScanLock)
    }

    private func submitManualPayload() {
        let trimmedPayload = manualPayloadText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPayload.isEmpty else {
            manualPayloadError = "Paste the pairing payload from Remodex on your Mac."
            return
        }

        submitPairingCode(trimmedPayload, isManualEntry: true)
    }

    private func submitPairingCode(
        _ code: String,
        resetScanLock: (() -> Void)? = nil,
        isManualEntry: Bool = false
    ) {
        switch validatePairingQRCode(code) {
        case .success(let payload):
            manualPayloadText = ""
            manualPayloadError = nil
            isShowingManualPayloadSheet = false
            onScan(payload)
        case .scanError(let message):
            if isManualEntry {
                manualPayloadError = message
            } else {
                scannerError = message
            }
            resetScanLock?()
        case .bridgeUpdateRequired(let prompt):
            didCopyBridgeUpdateCommand = false
            manualPayloadText = ""
            manualPayloadError = nil
            isShowingManualPayloadSheet = false
            bridgeUpdatePrompt = prompt
            resetScanLock?()
        }
    }

    private func pastePayloadFromClipboard() {
        guard let clipboardText = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines),
              !clipboardText.isEmpty else {
            manualPayloadError = "Clipboard does not contain a pairing payload."
            return
        }

        manualPayloadError = nil
        manualPayloadText = clipboardText
    }

    private func dismissManualPayloadSheet() {
        manualPayloadError = nil
        isShowingManualPayloadSheet = false
    }
}

private extension CodexBridgeUpdatePrompt {
    static let previewScannerMismatch = CodexBridgeUpdatePrompt(
        title: "Update Remodex on your Mac before scanning",
        message: "This QR code was generated by a different Remodex npm version. Update the package on your Mac to the latest release before scanning a new QR code.",
        command: "npm install -g remodex@latest"
    )
}

// MARK: - Preview

#Preview("Bridge Update Required") {
    QRScannerView(
        initialBridgeUpdatePrompt: .previewScannerMismatch,
        initialIsCheckingPermission: false,
        onBack: {}
    ) { _ in }
}

private struct ManualPairingPayloadSheet: View {
    @Binding var payloadText: String
    @Binding var errorMessage: String?
    let onPasteFromClipboard: () -> Void
    let onConnect: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Pair with payload")
                        .font(AppFont.title3(weight: .semibold))

                    Text("Paste the full JSON pairing payload from Remodex on your Mac. This uses the same secure pairing flow as the QR code.")
                        .font(AppFont.body())
                        .foregroundStyle(.secondary)
                }

                TextEditor(text: $payloadText)
                    .font(AppFont.mono(.subheadline))
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .padding(12)
                    .frame(minHeight: 180)
                    .scrollContentBackground(.hidden)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(Color(.secondarySystemFill))
                    )

                Text("Tip: you can copy the payload from your terminal and paste it here directly.")
                    .font(AppFont.caption())
                    .foregroundStyle(.secondary)

                VStack(spacing: 10) {
                    Button(action: onPasteFromClipboard) {
                        Text("Paste from Clipboard")
                            .font(AppFont.body(weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(
                                RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    .fill(Color(.secondarySystemFill))
                            )
                    }
                    .buttonStyle(.plain)

                    Button(action: onConnect) {
                        Text("Connect")
                            .font(AppFont.body(weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .foregroundStyle(.white)
                            .background(.black, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(trimmedPayloadText.isEmpty)
                    .opacity(trimmedPayloadText.isEmpty ? 0.5 : 1)
                }

                Spacer(minLength: 0)
            }
            .padding(20)
            .navigationTitle("Manual Pairing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", action: onDismiss)
                }
            }
            .alert("Pairing Error", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {
                    errorMessage = nil
                }
            } message: {
                Text(errorMessage ?? "Invalid pairing payload")
            }
        }
    }

    private var trimmedPayloadText: String {
        payloadText.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

// MARK: - Camera Preview UIViewRepresentable

private struct QRCameraPreview: UIViewRepresentable {
    let onScan: (String, _ resetScanLock: @escaping () -> Void) -> Void

    func makeUIView(context: Context) -> QRCameraUIView {
        let view = QRCameraUIView()
        view.onScan = { [weak view] code in
            onScan(code) {
                view?.resetScanLock()
            }
        }
        return view
    }

    func updateUIView(_ uiView: QRCameraUIView, context: Context) {}

    // Tears down the camera before UIKit deallocates the preview layer.
    static func dismantleUIView(_ uiView: QRCameraUIView, coordinator: ()) {
        uiView.stopCamera()
    }
}

// Serializes camera session handoff so a fast reopen cannot start before the previous stop completes.
private final class QRCameraLifecycleCoordinator {
    static let shared = QRCameraLifecycleCoordinator()
    private typealias DeferredStart = () -> Void

    private let queue = DispatchQueue(label: "com.phodex.qr-camera.lifecycle")
    private let lock = NSLock()
    private var isStopInFlight = false
    private var deferredStarts: [DeferredStart] = []

    // Starts immediately unless a previous stop still owns the camera handoff.
    func start(session: AVCaptureSession, canStart: @escaping () -> Bool) {
        let startWork: DeferredStart = { [queue] in
            queue.async {
                guard canStart(), !session.isRunning else {
                    return
                }
                session.startRunning()
            }
        }

        guard !deferStartIfNeeded(startWork) else {
            return
        }

        startWork()
    }

    // Holds new starts until stopRunning completes, then replays any deferred opens.
    func stop(session: AVCaptureSession) {
        lock.lock()
        isStopInFlight = true
        lock.unlock()

        queue.async { [weak self] in
            guard session.isRunning else {
                self?.finishStopAndReplayDeferredStarts()
                return
            }

            session.stopRunning()
            self?.finishStopAndReplayDeferredStarts()
        }
    }

    // Reopens queued scanners only after the previous session fully releases the camera.
    private func finishStopAndReplayDeferredStarts() {
        lock.lock()
        let startsToReplay = deferredStarts
        deferredStarts.removeAll()
        isStopInFlight = false
        lock.unlock()

        startsToReplay.forEach { start in
            start()
        }
    }

    // Converts overlapping reopen attempts into deferred starts while teardown is active.
    private func deferStartIfNeeded(_ startWork: @escaping DeferredStart) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        guard isStopInFlight else {
            return false
        }

        deferredStarts.append(startWork)
        return true
    }
}

// Owns the AVFoundation session lifecycle for the SwiftUI scanner host view.
private class QRCameraUIView: UIView, AVCaptureMetadataOutputObjectsDelegate {
    var onScan: ((String) -> Void)?

    private let captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var hasScanned = false
    private var isStoppingCamera = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupCamera()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupCamera()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }

    // Configures the metadata session once and starts it off the main thread.
    private func setupCamera() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device) else {
            return
        }

        if captureSession.canAddInput(input) {
            captureSession.addInput(input)
        }

        let output = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(output) {
            captureSession.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]
        }

        let layer = AVCaptureVideoPreviewLayer(session: captureSession)
        layer.videoGravity = .resizeAspectFill
        self.layer.addSublayer(layer)
        previewLayer = layer

        QRCameraLifecycleCoordinator.shared.start(session: captureSession) { [weak self] in
            guard let self else {
                return false
            }
            return !self.isStoppingCamera
        }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !hasScanned,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              object.type == .qr,
              let code = object.stringValue else {
            return
        }

        hasScanned = true
        HapticFeedback.shared.triggerImpactFeedback(style: .heavy)
        onScan?(code)
    }

    func resetScanLock() {
        hasScanned = false
    }

    // Detaches the preview layer first so AVFoundation teardown stays serialized.
    func stopCamera() {
        guard !isStoppingCamera else {
            return
        }

        isStoppingCamera = true
        onScan = nil

        let layerToRemove = previewLayer
        previewLayer = nil
        layerToRemove?.session = nil
        layerToRemove?.removeFromSuperlayer()

        QRCameraLifecycleCoordinator.shared.stop(session: captureSession)
    }

    deinit {
        stopCamera()
    }
}
