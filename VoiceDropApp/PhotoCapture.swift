import SwiftUI
import AVFoundation

// MARK: - SwiftUI wrapper

struct PhotoCaptureView: UIViewControllerRepresentable {
    var onCapture: (Date, Data) -> Void
    var onCancel: () -> Void

    func makeUIViewController(context: Context) -> PhotoCaptureVC {
        let vc = PhotoCaptureVC()
        vc.onCaptured = onCapture
        vc.onCancelled = onCancel
        return vc
    }

    func updateUIViewController(_ vc: PhotoCaptureVC, context: Context) {}
}

// MARK: - View Controller

/// Camera capture that runs AVCaptureSession with a video-only input.
/// No audio input is added, so the ongoing AVAudioRecorder session is not
/// interrupted — this is the key difference from UIImagePickerController.
final class PhotoCaptureVC: UIViewController {
    var onCaptured: ((Date, Data) -> Void)?
    var onCancelled: (() -> Void)?

    private let captureSession = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "com.vd.photo.session")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var frameBorder: CALayer?
    private var currentDelegate: PhotoDelegate?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCapture()
        setupUI()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // WYSIWYG: the preview is a centered square equal to the final crop, so
        // what you see in the square is exactly what gets saved.
        previewLayer?.frame = squareFrame
        frameBorder?.frame = squareFrame
    }

    private var squareFrame: CGRect {
        let side = view.bounds.width
        return CGRect(x: 0, y: (view.bounds.height - side) / 2, width: side, height: side)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        sessionQueue.async { self.captureSession.stopRunning() }
    }

    private func setupCapture() {
        guard
            let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
            let input = try? AVCaptureDeviceInput(device: device),
            captureSession.canAddInput(input),
            captureSession.canAddOutput(photoOutput)
        else { return }

        captureSession.beginConfiguration()
        captureSession.sessionPreset = .photo
        captureSession.addInput(input)
        captureSession.addOutput(photoOutput)
        captureSession.commitConfiguration()

        let preview = AVCaptureVideoPreviewLayer(session: captureSession)
        preview.videoGravity = .resizeAspectFill   // fills the square; center-crops sensor
        view.layer.insertSublayer(preview, at: 0)
        previewLayer = preview

        // Lock both the preview and the photo output to portrait (90° clockwise),
        // so the preview's center square and the saved photo's center square are
        // the same region — otherwise one crops top/bottom and the other left/right.
        let angle: CGFloat = 90
        if let pc = preview.connection, pc.isVideoRotationAngleSupported(angle) {
            pc.videoRotationAngle = angle
        }
        if let oc = photoOutput.connection(with: .video), oc.isVideoRotationAngleSupported(angle) {
            oc.videoRotationAngle = angle
        }

        // A thin border marks the exact square that will be saved.
        let border = CALayer()
        border.borderColor = UIColor.white.withAlphaComponent(0.35).cgColor
        border.borderWidth = 1
        view.layer.addSublayer(border)
        frameBorder = border

        sessionQueue.async { self.captureSession.startRunning() }
    }

    private func setupUI() {
        let shutter = UIButton(type: .custom)
        shutter.translatesAutoresizingMaskIntoConstraints = false
        shutter.backgroundColor = .white
        shutter.layer.cornerRadius = 36
        shutter.layer.borderWidth = 4
        shutter.layer.borderColor = UIColor.white.withAlphaComponent(0.5).cgColor
        shutter.addTarget(self, action: #selector(takePhoto), for: .touchUpInside)
        view.addSubview(shutter)

        let cancelBtn = UIButton(type: .system)
        cancelBtn.translatesAutoresizingMaskIntoConstraints = false
        cancelBtn.setTitle("取消", for: .normal)
        cancelBtn.setTitleColor(.white, for: .normal)
        cancelBtn.titleLabel?.font = .systemFont(ofSize: 17, weight: .medium)
        cancelBtn.addTarget(self, action: #selector(cancel), for: .touchUpInside)
        view.addSubview(cancelBtn)

        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            shutter.widthAnchor.constraint(equalToConstant: 72),
            shutter.heightAnchor.constraint(equalToConstant: 72),
            shutter.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            shutter.bottomAnchor.constraint(equalTo: safe.bottomAnchor, constant: -24),

            cancelBtn.centerYAnchor.constraint(equalTo: shutter.centerYAnchor),
            cancelBtn.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 28)
        ])
    }

    @objc private func takePhoto() {
        let delegate = PhotoDelegate { [weak self] date, jpeg in
            self?.onCaptured?(date, jpeg)
        } onCancel: { [weak self] in
            self?.onCancelled?()
        }
        currentDelegate = delegate
        photoOutput.capturePhoto(with: AVCapturePhotoSettings(), delegate: delegate)
    }

    @objc private func cancel() {
        onCancelled?()
    }
}

// MARK: - Delegate (separate class to avoid @MainActor isolation issues)

private final class PhotoDelegate: NSObject, AVCapturePhotoCaptureDelegate, @unchecked Sendable {
    private let onCapture: (Date, Data) -> Void
    private let onCancel: () -> Void

    init(onCapture: @escaping (Date, Data) -> Void, onCancel: @escaping () -> Void) {
        self.onCapture = onCapture
        self.onCancel = onCancel
    }

    func photoOutput(_ output: AVCapturePhotoOutput,
                     didFinishProcessingPhoto photo: AVCapturePhoto,
                     error: Error?) {
        guard error == nil,
              let raw = photo.fileDataRepresentation(),
              let image = UIImage(data: raw),
              let jpeg = Self.squareJPEG(image)
        else { DispatchQueue.main.async { self.onCancel() }; return }
        let date = Date()
        DispatchQueue.main.async { self.onCapture(date, jpeg) }
    }

    /// Center-crop the photo to a 1:1 square in DISPLAY orientation (matching the
    /// square preview), downscale the longest side to ≤1080px, and JPEG-encode
    /// stepping the quality down until the file is under ~900KB (target <1MB).
    private static func squareJPEG(_ image: UIImage, maxSide: CGFloat = 1080,
                                   maxBytes: Int = 900_000) -> Data? {
        // image.size is in display orientation (e.g. portrait 3024×4032); the
        // center square there is exactly what the square preview showed.
        let s = image.size
        let cropSide = min(s.width, s.height)
        let origin = CGPoint(x: (s.width - cropSide) / 2, y: (s.height - cropSide) / 2)

        let outSide = min(cropSide * image.scale, maxSide)   // target pixels
        let fmt = UIGraphicsImageRendererFormat.default()
        fmt.scale = 1                                         // 1 point == 1 pixel
        fmt.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: outSide, height: outSide), format: fmt)
        let square = renderer.image { ctx in
            let k = outSide / cropSide
            ctx.cgContext.scaleBy(x: k, y: k)
            // draw(at:) applies the image's EXIF orientation, so the result is
            // upright; the offset selects the center square.
            image.draw(at: CGPoint(x: -origin.x, y: -origin.y))
        }

        var q: CGFloat = 0.8
        var data = square.jpegData(compressionQuality: q)
        while let d = data, d.count > maxBytes, q > 0.4 {
            q -= 0.1
            data = square.jpegData(compressionQuality: q)
        }
        return data
    }
}
