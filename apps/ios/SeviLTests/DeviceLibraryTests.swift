import XCTest
import PDFKit
import UIKit
@testable import SeviL

final class DeviceLibraryTests: XCTestCase {
    func testIndependentLibraryAndPDF() async throws {
        let api=API()
        let course: Course=try await api.get("/v1/courses",method:"POST",json:["title":"Synthetic physics"])
        let session: LectureSession=try await api.get("/v1/sessions",method:"POST",json:["title":"Offline lecture","course_id":course.id,"recording_permission":true])
        let file=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString+".txt")
        try Data("Synthetic textbook extract".utf8).write(to:file)
        defer { try? FileManager.default.removeItem(at:file) }
        try await api.upload(session:session.id,file:file,name:"chapter.txt",kind:"resource",offset:12500)
        let saved: LectureSession=try await api.get("/v1/sessions/"+session.id)
        let asset=try XCTUnwrap(saved.assets?.first)
        XCTAssertEqual(asset.offset_ms,12500)
        let bytes=try await api.data("/v1/assets/"+asset.id)
        XCTAssertEqual(String(data:bytes,encoding:.utf8),"Synthetic textbook extract")
        let reopened=try DeviceStore()
        XCTAssertEqual(try reopened.get("session",session.id)["title"] as? String,"Offline lecture")
        let board=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString+".png")
        let image=UIGraphicsImageRenderer(size:CGSize(width:80,height:60)).image { context in UIColor.green.setFill();context.fill(CGRect(x:0,y:0,width:80,height:60)) }
        try XCTUnwrap(image.pngData()).write(to:board)
        defer { try? FileManager.default.removeItem(at:board) }
        try await api.upload(session:session.id,file:board,name:"board.png",kind:"photo",offset:12500)
        let _: LectureSession=try await api.get("/v1/sessions/"+session.id+"/demo",method:"POST",json:[:])
        let pdf=try await api.data("/v1/sessions/"+session.id+"/handout.pdf")
        let document=try XCTUnwrap(PDFDocument(data:pdf))
        XCTAssertGreaterThanOrEqual(document.pageCount,2)
        XCTAssertTrue(document.string?.contains("captured at 12s")==true)
        try Keychain.save("",account:"gemini")
        do {
            let _: LectureSession=try await api.get("/v1/sessions/"+session.id+"/generate",method:"POST",json:["provider":"gemini"])
            XCTFail("Missing key should fail before network")
        } catch { XCTAssertTrue(error.localizedDescription.contains("Settings")) }
        _=try await api.data("/v1/sessions/"+session.id,method:"DELETE")
        let retained=try await api.data("/v1/assets/"+asset.id)
        XCTAssertEqual(retained,bytes)
        _=try await api.data("/v1/assets/"+asset.id,method:"DELETE")
    }
    func testDeviceKeyCanBeSavedAndCleared() throws {
        try Keychain.save("synthetic-not-a-secret",account:"gemini")
        XCTAssertEqual(Keychain.read("gemini"),"synthetic-not-a-secret")
        try Keychain.save("",account:"gemini");XCTAssertEqual(Keychain.read("gemini"),"")
    }
    func testSessionLinksAndConversationIsolation() async throws {
        let api=API()
        let course:Course=try await api.get("/v1/courses",method:"POST",json:["title":"Synthetic tutor course"])
        let a:LectureSession=try await api.get("/v1/sessions",method:"POST",json:["title":"Session A","course_id":course.id,"recording_permission":true])
        let b:LectureSession=try await api.get("/v1/sessions",method:"POST",json:["title":"Session B","course_id":course.id,"recording_permission":true])
        let _:SessionLink=try await api.get("/v1/sessions/"+a.id+"/links",method:"POST",json:["title":"Reading","url":"https://example.edu/reading","text":"Momentum is mass times velocity."])
        let linksA:[SessionLink]=try await api.get("/v1/sessions/"+a.id+"/links")
        let linksB:[SessionLink]=try await api.get("/v1/sessions/"+b.id+"/links")
        XCTAssertEqual(linksA.count,1);XCTAssertTrue(linksB.isEmpty)
        let messages:[ChatMessage]=try await api.get("/v1/sessions/"+b.id+"/chat")
        XCTAssertTrue(messages.isEmpty)
        do {let _:SessionLink=try await api.get("/v1/sessions/"+b.id+"/links",method:"POST",json:["title":"Bad","url":"javascript:alert(1)"]);XCTFail("Unsafe link accepted")} catch {}
        _=try await api.data("/v1/sessions/"+a.id,method:"DELETE");_=try await api.data("/v1/sessions/"+b.id,method:"DELETE")
    }
    func testTutorContextAndCitations() throws {
        let text=String(repeating:"x",count:60000)+" angular momentum conservation "+String(repeating:"y",count:60000)
        let excerpt=ChatContext.excerpt(text,question:"angular momentum",limit:5000)
        XCTAssertTrue(excerpt.contains("angular momentum"));XCTAssertLessThanOrEqual(excerpt.count,5000)
        XCTAssertThrowsError(try ChatContext.validate(["answer":"A reply","citations":["invented"],"links":[]],sources:[]))
    }

}
