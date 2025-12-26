import Foundation
import Capacitor
import AVFoundation
import UIKit
import Vision

@objc(BmQrPlugin)
public class BmQrPlugin: CAPPlugin {
    private var call: CAPPluginCall?
    private var qrViewController: QRViewController?

    @objc public func echo(_ call: CAPPluginCall) {
        self.call = call
        let value = call.getString("value") ?? "ru"
        let fromGallery = call.getString("fromGallery") ?? "no"

        DispatchQueue.main.async {
            let vc = QRViewController()
            vc.delegate = self
            vc.language = value
            vc.fromGallery = fromGallery
            vc.modalPresentationStyle = .fullScreen

            self.qrViewController = vc
            self.bridge?.viewController?.present(vc, animated: true)
        }
    }
}

extension BmQrPlugin: QRViewControllerDelegate {
    func didScanQRCode(value: String) {
        call?.resolve(["result": value])
        qrViewController?.dismiss(animated: true)
        qrViewController = nil
        call = nil
    }

    func didCancelQRCode() {
        call?.reject("bmQr Activity было отменено")
        qrViewController?.dismiss(animated: true)
        qrViewController = nil
        call = nil
    }
}

// MARK: - QRViewControllerDelegate

protocol QRViewControllerDelegate: AnyObject {
    func didScanQRCode(value: String)
    func didCancelQRCode()
}

// MARK: - QRViewController

final class QRViewController: UIViewController,
                             AVCaptureMetadataOutputObjectsDelegate,
                             UIImagePickerControllerDelegate,
                             UINavigationControllerDelegate {

    weak var delegate: QRViewControllerDelegate?

    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var videoDevice: AVCaptureDevice?

    var language: String = "ru"
    var fromGallery: String = "no"

    private var isFlashOn = false

    private var closeButton: UIButton!
    private var flashButton: UIButton!
    private var galleryButton: UIButton?

    // Overlay
    private var overlayLayer: CAShapeLayer?
    private var borderLayer: CAShapeLayer?
    private var scanRect: CGRect = .zero

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupUI()
        checkCameraPermission()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if captureSession?.isRunning == false {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession?.startRunning()
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession?.isRunning == true {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession?.stopRunning()
            }
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        previewLayer?.frame = view.layer.bounds

        closeButton.frame = CGRect(x: 20, y: 50, width: 40, height: 40)
        flashButton.frame = CGRect(x: view.bounds.width - 60, y: 50, width: 40, height: 40)

        if let galleryButton = galleryButton {
            galleryButton.frame = CGRect(x: 0, y: view.bounds.height - 100, width: 260, height: 44)
            galleryButton.center.x = view.center.x
        }

        // Перерисовать overlay при изменении размеров
        overlayLayer?.removeFromSuperlayer()
        borderLayer?.removeFromSuperlayer()
        addScanOverlay()

        // ✅ Кнопки поверх затемнения (важно!)
        view.bringSubviewToFront(closeButton)
        view.bringSubviewToFront(flashButton)
        if let galleryButton = galleryButton {
            view.bringSubviewToFront(galleryButton)
        }
    }

    // MARK: UI

    private func setupUI() {
        // Close button
        closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        closeButton.layer.cornerRadius = 20
        closeButton.addTarget(self, action: #selector(didTapClose), for: .touchUpInside)
        view.addSubview(closeButton)

        // Flash button
        flashButton = UIButton(type: .system)
        flashButton.setImage(UIImage(systemName: "bolt.slash.fill"), for: .normal)
        flashButton.tintColor = .white
        flashButton.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        flashButton.layer.cornerRadius = 20
        flashButton.addTarget(self, action: #selector(didTapFlash), for: .touchUpInside)
        view.addSubview(flashButton)

        // Gallery button
        if fromGallery.lowercased() == "yes" {
            let btn = UIButton(type: .system)
            let lng = language.lowercased()
            if lng == "tj" {
                btn.setTitle("Боргирии QR аз галерея", for: .normal)
            } else if lng == "ru" {
                btn.setTitle("QR загрузить с галереи", for: .normal)
            } else {
                btn.setTitle("Scan QR from gallery", for: .normal)
            }
            btn.setTitleColor(.white, for: .normal)
            btn.backgroundColor = UIColor.black.withAlphaComponent(0.5)
            btn.layer.cornerRadius = 10
            btn.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
            btn.addTarget(self, action: #selector(didTapGallery), for: .touchUpInside)
            view.addSubview(btn)
            galleryButton = btn
        }
    }

    // MARK: Permissions

    private func checkCameraPermission() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            setupCaptureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.setupCaptureSession()
                    } else {
                        self?.delegate?.didCancelQRCode()
                    }
                }
            }
        default:
            delegate?.didCancelQRCode()
        }
    }

    // MARK: Camera

    private func setupCaptureSession() {
        let session = AVCaptureSession()
        session.sessionPreset = .high
        captureSession = session

        guard let device = AVCaptureDevice.default(for: .video) else {
            delegate?.didCancelQRCode()
            return
        }
        videoDevice = device

        do {
            let input = try AVCaptureDeviceInput(device: device)
            if session.canAddInput(input) { session.addInput(input) }
            else { delegate?.didCancelQRCode(); return }
        } catch {
            delegate?.didCancelQRCode()
            return
        }

        let metadataOutput = AVCaptureMetadataOutput()
        if session.canAddOutput(metadataOutput) {
            session.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            metadataOutput.metadataObjectTypes = [.qr]
        } else {
            delegate?.didCancelQRCode()
            return
        }

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.frame = view.layer.bounds
        layer.videoGravity = .resizeAspectFill
        previewLayer = layer
        view.layer.insertSublayer(layer, at: 0)

        // Рисуем рамку
        addScanOverlay()

        // Ограничиваем область скана рамкой:
        if let rect = previewLayer?.metadataOutputRectConverted(fromLayerRect: scanRect) {
            metadataOutput.rectOfInterest = rect
        }

        // Кнопки поверх overlay
        view.bringSubviewToFront(closeButton)
        view.bringSubviewToFront(flashButton)
        if let galleryButton = galleryButton {
            view.bringSubviewToFront(galleryButton)
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession?.startRunning()
        }
    }

    // MARK: Overlay рамка по центру

    private func addScanOverlay() {
        guard view.bounds.width > 0, view.bounds.height > 0 else { return }

        let size: CGFloat = 260
        scanRect = CGRect(
            x: (view.bounds.width - size) / 2,
            y: (view.bounds.height - size) / 2,
            width: size,
            height: size
        )

        let path = UIBezierPath(rect: view.bounds)
        let hole = UIBezierPath(roundedRect: scanRect, cornerRadius: 16)
        path.append(hole)
        path.usesEvenOddFillRule = true

        let overlay = CAShapeLayer()
        overlay.path = path.cgPath
        overlay.fillRule = .evenOdd
        overlay.fillColor = UIColor.black.withAlphaComponent(0.6).cgColor
        view.layer.addSublayer(overlay)
        overlayLayer = overlay

        let border = CAShapeLayer()
        border.path = UIBezierPath(roundedRect: scanRect, cornerRadius: 16).cgPath
        border.strokeColor = UIColor.white.cgColor
        border.lineWidth = 2
        border.fillColor = UIColor.clear.cgColor
        view.layer.addSublayer(border)
        borderLayer = border
    }

    // MARK: AVCaptureMetadataOutputObjectsDelegate

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {

        guard let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = obj.stringValue else { return }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession?.stopRunning()
        }

        DispatchQueue.main.async { [weak self] in
            self?.delegate?.didScanQRCode(value: value)
        }
    }

    // MARK: Buttons

    @objc private func didTapClose() {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession?.stopRunning()
        }
        delegate?.didCancelQRCode()
    }

    @objc private func didTapFlash() {
        guard let device = videoDevice, device.hasTorch else { return }

        do {
            try device.lockForConfiguration()
            if isFlashOn {
                device.torchMode = .off
                flashButton.setImage(UIImage(systemName: "bolt.slash.fill"), for: .normal)
                isFlashOn = false
            } else {
                try device.setTorchModeOn(level: 1.0)
                flashButton.setImage(UIImage(systemName: "bolt.fill"), for: .normal)
                isFlashOn = true
            }
            device.unlockForConfiguration()
        } catch {
            print("Error toggling flash: \(error)")
        }
    }

    @objc private func didTapGallery() {
        let picker = UIImagePickerController()
        picker.delegate = self
        picker.sourceType = .photoLibrary
        picker.allowsEditing = false
        present(picker, animated: true)
    }

    // MARK: UIImagePickerControllerDelegate

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }

    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        picker.dismiss(animated: true)

        guard let image = info[.originalImage] as? UIImage else {
            delegate?.didCancelQRCode()
            return
        }
        scanQRFromImage(image)
    }

    // MARK: Vision scan from image

    private func scanQRFromImage(_ image: UIImage) {
        guard let cgImage = image.cgImage else {
            delegate?.didCancelQRCode()
            return
        }

        let request = VNDetectBarcodesRequest { [weak self] req, err in
            guard let self else { return }

            if let err {
                print("Error scanning QR: \(err)")
                DispatchQueue.main.async { self.delegate?.didCancelQRCode() }
                return
            }

            guard let results = req.results as? [VNBarcodeObservation],
                  let first = results.first,
                  let payload = first.payloadStringValue else {
                DispatchQueue.main.async { self.delegate?.didCancelQRCode() }
                return
            }

            DispatchQueue.main.async {
                self.delegate?.didScanQRCode(value: payload)
            }
        }

        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        do {
            try handler.perform([request])
        } catch {
            print("Error performing QR scan: \(error)")
            delegate?.didCancelQRCode()
        }
    }
}
