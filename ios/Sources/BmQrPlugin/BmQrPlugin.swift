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
        let value = call.getString("value") ?? ""
        let fromGallery = call.getString("fromGallery") ?? ""
        
        DispatchQueue.main.async {
            self.qrViewController = QRViewController()
            self.qrViewController?.delegate = self
            self.qrViewController?.language = value
            self.qrViewController?.fromGallery = fromGallery
            self.qrViewController?.modalPresentationStyle = .fullScreen
            self.bridge?.viewController?.present(self.qrViewController!, animated: true)
        }
    }
}

// MARK: - QRViewControllerDelegate

extension BmQrPlugin: QRViewControllerDelegate {
    func didScanQRCode(value: String) {
        call?.resolve([
            "result": value
        ])
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

// MARK: - QRViewController

protocol QRViewControllerDelegate: AnyObject {
    func didScanQRCode(value: String)
    func didCancelQRCode()
}

class QRViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    weak var delegate: QRViewControllerDelegate?
    var captureSession: AVCaptureSession?
    var previewLayer: AVCaptureVideoPreviewLayer?
    var videoDevice: AVCaptureDevice?
    var language: String = ""
    var fromGallery: String = ""
    var isFlashOn = false
    
    private var closeButton: UIButton!
    private var flashButton: UIButton!
    private var galleryButton: UIButton!

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
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
        
        // Update button positions
        closeButton.frame = CGRect(x: 20, y: 50, width: 40, height: 40)
        flashButton.frame = CGRect(x: view.bounds.width - 60, y: 50, width: 40, height: 40)
        if let galleryButton = galleryButton {
            galleryButton.frame = CGRect(x: 0, y: view.bounds.height - 100, width: 250, height: 44)
            galleryButton.center.x = view.center.x
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
    
    func setupUI() {
        // Close button
        closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        closeButton.layer.cornerRadius = 20
        closeButton.frame = CGRect(x: 20, y: 50, width: 40, height: 40)
        closeButton.addTarget(self, action: #selector(didTapClose), for: .touchUpInside)
        view.addSubview(closeButton)
        
        // Flash button
        flashButton = UIButton(type: .system)
        flashButton.setImage(UIImage(systemName: "bolt.slash.fill"), for: .normal)
        flashButton.tintColor = .white
        flashButton.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        flashButton.layer.cornerRadius = 20
        flashButton.frame = CGRect(x: view.bounds.width - 60, y: 50, width: 40, height: 40)
        flashButton.addTarget(self, action: #selector(didTapFlash), for: .touchUpInside)
        view.addSubview(flashButton)
        
        // Gallery button (only if fromGallery is "yes")
        if fromGallery.lowercased() == "yes" {
            galleryButton = UIButton(type: .system)
            if language.lowercased() == "tj" {
                galleryButton.setTitle("Боргирии QR аз галерея", for: .normal)
            } else if language.lowercased() == "ru" {
                galleryButton.setTitle("QR загрузить с галереи", for: .normal)
            } else {
                galleryButton.setTitle("Считать QR галереи", for: .normal)
            }
            galleryButton.setTitleColor(.white, for: .normal)
            galleryButton.backgroundColor = UIColor.systemBlue
            galleryButton.layer.cornerRadius = 8
            galleryButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
            galleryButton.frame = CGRect(x: 0, y: view.bounds.height - 100, width: 250, height: 44)
            galleryButton.center.x = view.center.x
            galleryButton.addTarget(self, action: #selector(didTapGallery), for: .touchUpInside)
            view.addSubview(galleryButton)
        }
    }
    
    func checkCameraPermission() {
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

    func setupCaptureSession() {
        captureSession = AVCaptureSession()
        captureSession?.sessionPreset = .high
        
        guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else {
            delegate?.didCancelQRCode()
            return
        }
        
        videoDevice = videoCaptureDevice
        
        do {
            let videoInput = try AVCaptureDeviceInput(device: videoCaptureDevice)
            if captureSession?.canAddInput(videoInput) == true {
                captureSession?.addInput(videoInput)
            } else {
                delegate?.didCancelQRCode()
                return
            }
        } catch {
            delegate?.didCancelQRCode()
            return
        }

        let metadataOutput = AVCaptureMetadataOutput()
        if captureSession?.canAddOutput(metadataOutput) == true {
            captureSession?.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            metadataOutput.metadataObjectTypes = [.qr]
        } else {
            delegate?.didCancelQRCode()
            return
        }

        previewLayer = AVCaptureVideoPreviewLayer(session: captureSession!)
        previewLayer?.frame = view.layer.bounds
        previewLayer?.videoGravity = .resizeAspectFill
        if let previewLayer = previewLayer {
            view.layer.insertSublayer(previewLayer, at: 0)
        }
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession?.startRunning()
        }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        if let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
           let stringValue = metadataObject.stringValue {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession?.stopRunning()
            }
            DispatchQueue.main.async { [weak self] in
                self?.delegate?.didScanQRCode(value: stringValue)
            }
        }
    }

    @objc func didTapClose() {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession?.stopRunning()
        }
        delegate?.didCancelQRCode()
    }
    
    @objc func didTapFlash() {
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
    
    @objc func didTapGallery() {
        let imagePicker = UIImagePickerController()
        imagePicker.delegate = self
        imagePicker.sourceType = .photoLibrary
        imagePicker.allowsEditing = false
        present(imagePicker, animated: true)
    }
    
    // MARK: - UIImagePickerControllerDelegate
    
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        picker.dismiss(animated: true)
        
        if let image = info[.originalImage] as? UIImage {
            scanQRFromImage(image)
        } else {
            delegate?.didCancelQRCode()
        }
    }
    
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }
    
    func scanQRFromImage(_ image: UIImage) {
        guard let cgImage = image.cgImage else {
            delegate?.didCancelQRCode()
            return
        }
        
        let request = VNDetectBarcodesRequest { [weak self] request, error in
            guard let self = self else { return }
            
            if let error = error {
                print("Error scanning QR: \(error)")
                DispatchQueue.main.async {
                    self.delegate?.didCancelQRCode()
                }
                return
            }
            
            guard let results = request.results as? [VNBarcodeObservation],
                  let firstResult = results.first,
                  let payload = firstResult.payloadStringValue else {
                DispatchQueue.main.async {
                    self.delegate?.didCancelQRCode()
                }
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