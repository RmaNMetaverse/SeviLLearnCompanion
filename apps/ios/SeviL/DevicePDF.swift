import UIKit
import CoreText

enum DevicePDF {
    static func render(_ session: [String:Any],course: [String:Any],assets: [[String:Any]],store: DeviceStore) throws -> Data {
        let note=session["handout"] as! [String:Any]
        let text=NSMutableAttributedString(string:"")
        func add(_ value: String,_ size: CGFloat=11,_ bold: Bool=false) {
            let paragraph=NSMutableParagraphStyle();paragraph.lineSpacing=4;paragraph.paragraphSpacing=10
            text.append(NSAttributedString(string:value+"\n",attributes:[.font:bold ? UIFont.boldSystemFont(ofSize:size):UIFont.systemFont(ofSize:size),.foregroundColor:UIColor(red:0.14,green:0.23,blue:0.22,alpha:1),.paragraphStyle:paragraph]))
        }
        add("SeviL / LECTURE NOTES",10,true);add(note["title"] as! String,23,true);add([course["title"],course["university"],course["professor"]].compactMap {$0 as? String}.joined(separator:" · "),9);add("Generated with: "+(session["generated_provider"] as? String ?? ""),9);add(note["summary"] as! String)
        add("Learning objectives",16,true);for item in note["objectives"] as! [String] { add("• "+item) }
        let sources=session["sources"] as? [[String:Any]] ?? []
        for s in note["sections"] as! [[String:Any]] { add(s["heading"] as! String,16,true);add((s["kind"] as! String).uppercased(),9);add(s["body"] as! String);for ref in s["citations"] as! [String] { add(sources.first { $0["id"] as? String==ref }?["label"] as? String ?? ref,9) } }
        add("Bilingual glossary",16,true);for term in note["glossary"] as! [[String:String]] { add(term["term"]!+" / "+term["translation"]!,12,true);add(term["definition"]!) }
        add("Review questions",16,true);for item in note["questions"] as! [String] { add("• "+item) }
        add("Check before studying",16,true);for item in note["uncertainties"] as! [String] { add("• "+item) }
        let photos=try assets.filter { $0["kind"] as? String=="photo" }.map { a -> (UIImage,String) in guard let image=UIImage(contentsOfFile:try store.file(a["id"] as! String).path) else { throw APIError.message("Cannot read saved board image") };return (image,(a["name"] as! String)+" · captured at \((a["offset_ms"] as? Int ?? 0)/1000)s") }
        let bounds=CGRect(x:0,y:0,width:595,height:842),frame=CGRect(x:48,y:48,width:499,height:742)
        let renderer=UIGraphicsPDFRenderer(bounds:bounds)
        return renderer.pdfData { context in
            let setter=CTFramesetterCreateWithAttributedString(text);var offset=0,page=0
            func begin() { context.beginPage();page+=1;("SeviL · \(page)" as NSString).draw(at:CGPoint(x:48,y:813),withAttributes:[.font:UIFont.systemFont(ofSize:9),.foregroundColor:UIColor.gray]) }
            while offset<text.length {
                begin();let cg=context.cgContext;cg.saveGState();cg.translateBy(x:0,y:842);cg.scaleBy(x:1,y:-1)
                let path=CGPath(rect:frame,transform:nil);let rendered=CTFramesetterCreateFrame(setter,CFRange(location:offset,length:0),path,nil);CTFrameDraw(rendered,cg);let range=CTFrameGetVisibleStringRange(rendered);cg.restoreGState();guard range.length>0 else { break };offset+=range.length
            }
            for (image,caption) in photos { begin();("Whiteboard evidence\n"+caption as NSString).draw(in:CGRect(x:48,y:48,width:499,height:65),withAttributes:[.font:UIFont.systemFont(ofSize:12)]);let scale=min(499/image.size.width,650/image.size.height);image.draw(in:CGRect(x:48,y:125,width:image.size.width*scale,height:image.size.height*scale)) }
        }
    }
}
