//
//  ZipImplementation.swift
//  iosApp
//
//  Created by Anthony Hofmeister on 30.10.25.
//  Copyright © 2025 orgName. All rights reserved.
//

import ComposeApp
import ZIPFoundation
import zlib

class ZipImplementation : ZipAPI {
    func decode(data: Data) -> Data? {
        if data.count < 2 || data[0] != 0x1f || data[1] != 0x8b {
            return nil
        }
            
        return gunzip(data: data)
    }
    
    func getEntries(data: Data) -> [IOSZipEntry] {
        guard let archive = Archive(data: data, accessMode: .read) else {
            return []
        }
        
        var result: [IOSZipEntry] = []
        
        for entry in archive {
            switch entry.type {
            case .directory:
                result.append(
                    IOSZipEntry(
                        path: entry.path,
                        data: nil,
                        isDirectory: true
                    )
                )
                
            case .file:
                var fileData = Data()
                do {
                    try archive.extract(entry) { chunk in
                        fileData.append(chunk)
                    }
                    
                    result.append(
                        IOSZipEntry(
                            path: entry.path,
                            data: fileData,
                            isDirectory: false
                        )
                    )
                } catch {
                    // wenn ein file nicht lesbar ist, kannst du entscheiden ob du es skippen willst
                    // ich skippe es jetzt einfach, weil wir keine Zeit für Drama haben
                    continue
                }
                
            default:
                // symlinks oder so, kannst du auch mappen wenn du willst
                continue
            }
        }
    
        return result
    }
    
    func getPaths(data: Data) -> [String] {
        guard let archive = Archive(data: data, accessMode: .read) else {
            return []
        }
        
        var result: [String] = []
        
        for entry in archive {
            result.append(entry.path)
        }
        
        return result
    }
    
    private func gunzip(data: Data) -> Data? {
        var stream = z_stream()
        var status: Int32

        // zlib initialisieren für gzip (15+16 = gzip)
        status = inflateInit2_(&stream, 16 + 15, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        if status != Z_OK {
            return nil
        }

        var output = Data(capacity: data.count * 2)

        data.withUnsafeBytes { (rawBuffer: UnsafeRawBufferPointer) in
            guard let baseAddress = rawBuffer.baseAddress else { return }
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: baseAddress.assumingMemoryBound(to: Bytef.self))
            stream.avail_in = uint(data.count)

            let chunkSize = 16 * 1024
            var buffer = [UInt8](repeating: 0, count: chunkSize)

            repeat {
                buffer.withUnsafeMutableBytes { outBuffer in
                    stream.next_out = outBuffer.baseAddress?.assumingMemoryBound(to: Bytef.self)
                    stream.avail_out = uInt(chunkSize)

                    status = inflate(&stream, Z_NO_FLUSH)

                    let have = chunkSize - Int(stream.avail_out)
                    if have > 0 {
                        if let base = outBuffer.baseAddress?.assumingMemoryBound(to: UInt8.self) {
                            let ptr = UnsafeBufferPointer(start: base, count: have)
                            output.append(ptr)
                        }
                    }
                }
            } while status == Z_OK
        }

        inflateEnd(&stream)

        if status == Z_STREAM_END {
            return output
        } else {
            return nil
        }
    }
}

