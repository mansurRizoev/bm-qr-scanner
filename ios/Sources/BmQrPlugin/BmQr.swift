import Foundation

@objc public class BmQr: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
