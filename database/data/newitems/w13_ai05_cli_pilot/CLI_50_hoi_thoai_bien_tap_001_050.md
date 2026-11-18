# W13 AI-05 — POSE Clinic | 50 hội thoại biên tập (bản đề xuất nghiệm thu nội dung)

**Trạng thái:** Editorial draft — chưa tích hợp generator/CSV, chưa chạy validator, chưa import DB. **Nguồn giá và mapping:** báo cáo W12 Clinic do người dùng cung cấp ngày 18/11/2026; chưa kiểm tra độc lập CSV W12 canonical.

**Phạm vi:** 50 hội thoại, 40 dev / 10 test; chỉ nhân viên tiếp nhận/CSKH trò chuyện với người bệnh hoặc người nhà. Bám sát đúng mã offering ACTIVE, giá, đơn vị tính trong báo cáo W12. Không tạo bác sĩ, lịch trống, kết quả xét nghiệm, tình trạng phòng, bảo hiểm hay chỉ định y khoa giả định. Trường hợp nguy hiểm: ưu tiên cấp cứu, không tư vấn bán dịch vụ.

**Lưu ý bắt buộc khi đối chiếu W12:** W12 chỉ xác nhận giá niêm yết, chưa xác nhận chính sách phí đã/chưa bao gồm xét nghiệm, quy trình trả kết quả, lịch hẹn hay năng lực tiếp nhận. Mọi câu đề cập chi phí bổ sung chỉ mô tả việc cần kiểm tra và thông báo trước, **không khẳng định chính sách chính thức**. Giá trong hội thoại là giá theo W12 do người dùng cung cấp, không phải báo giá cơ sở y tế thật.

**Định dạng:** KH = người bệnh/người nhà; NV = nhân viên CSKH/tiếp nhận. Không gán lời chẩn đoán hay chỉ định cho NV. Một số cuộc kết thúc mở theo hành vi khách thực tế.

---

## CLI-001 — Bé sợ khám sau lần lấy máu

- Split: **dev** · Link: `CUSTOMER` `11324b73-bbd8-5ec9-8c32-2d69bfb8600e` · Offering: `CLI-SP-PEDI` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Chào em, bé nhà chị 4 tuổi, lần trước lấy máu xong giờ nghe đi khám là khóc. Chị muốn đưa bé kiểm tra vì dạo này bé ăn ít.
- **NV:** Dạ, chị báo trước việc bé từng rất sợ lấy máu khi đăng ký để nhân viên lưu ý hỗ trợ nhé. Bé có đang mệt nhiều hay có biểu hiện bất thường cần khám sớm không chị?
- **KH:** Bé vẫn chơi nhưng hay kêu mệt. Chị không biết có phải làm xét nghiệm nữa không.
- **NV:** Mình nên để bác sĩ chuyên khoa Nhi thăm khám trước chị ạ. Nhu cầu xét nghiệm sẽ được trao đổi khi thăm khám trực tiếp.
- **KH:** Vậy khám nhi hết bao nhiêu em?
- **NV:** Phí khám Nhi khoa hiện là 1.800.000 đồng/lần. Phần xét nghiệm, nếu được chỉ định, sẽ cần kiểm tra chi phí riêng trước khi thực hiện chị nhé.
- **KH:** Để chị nói chuyện với ba bé rồi sắp xếp.
- **NV:** Dạ chị. Khi đăng ký, chị cứ nói rõ bé sợ thủ thuật để nhân viên hỗ trợ phù hợp ạ.

## CLI-002 — Khách ngại chia sẻ vấn đề phụ khoa

- Split: **dev** · Link: `CUSTOMER` `1b3090ff-53e7-593d-861f-9a979170f217` · Offering: `CLI-SP-OB` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Em ơi, chị muốn khám phụ khoa mà không tiện nói bệnh qua tin nhắn. Có bắt buộc kể hết mới đặt được không?
- **NV:** Dạ không chị. Chị chỉ cần cho biết mình muốn đăng ký khám Sản Phụ khoa; những thông tin sức khỏe riêng tư chị có thể trao đổi trực tiếp với bác sĩ trong buổi khám.
- **KH:** Chị hơi ngại, sợ ra quầy người ta hỏi lớn tiếng.
- **NV:** Em hiểu ạ. Chị có thể nói ngắn gọn là đến khám chuyên khoa và đề nghị trao đổi riêng các thông tin nhạy cảm. Em không cần chị gửi ảnh hay mô tả chi tiết qua Zalo.
- **KH:** Phí khám là bao nhiêu?
- **NV:** Phí khám Sản Phụ khoa theo danh mục hiện tại là 1.800.000 đồng/lần. Nếu có dịch vụ khác cần thực hiện, phòng khám sẽ kiểm tra và thông tin chi phí tương ứng.
- **KH:** Ừ, vậy chị yên tâm hơn rồi.

## CLI-003 — Người nhà nghi tình huống cấp cứu

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008238` · Offering: `CLI-EMG-ISOLATION` · Giá W12: **1.100.000 đồng/lần**

- **KH:** Mẹ tôi thở rất khó, người lơ mơ, tôi thấy bên mình có khám cấp cứu cách ly. Đưa qua được không?
- **NV:** Anh gọi 115 ngay hoặc đưa mẹ đến cơ sở cấp cứu gần nhất có khả năng tiếp nhận, đừng chờ xác nhận qua Zalo ạ. Tình trạng khó thở kèm lơ mơ cần được đánh giá khẩn cấp.
- **KH:** Tôi đang ở nhà, chưa biết phòng khám có khu cách ly không.
- **NV:** Hiện em không có thông tin xác nhận khả năng tiếp nhận cách ly và tình trạng trực cấp cứu tại thời điểm này. Anh ưu tiên liên hệ cấp cứu ngay; nếu có người ở cạnh, nhờ hỗ trợ trong lúc chờ nhân viên y tế.
- **KH:** Vậy tôi gọi 115 luôn.
- **NV:** Vâng anh, mình ưu tiên việc đó trước ạ.

## CLI-004 — Hỏi dịch vụ cấp cứu cách ly cho người nhà

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008239` · Offering: `CLI-EMG-ISOLATION` · Giá W12: **1.100.000 đồng/lần**

- **KH:** Cho mình hỏi khám cấp cứu cách ly 1 triệu mấy là sao? Người nhà mình mới sốt, muốn qua khám.
- **NV:** Danh mục của phòng khám có dịch vụ “Khám cấp cứu cách ly”, giá 1.100.000 đồng/lần. Tuy nhiên việc có cần cách ly hoặc tiếp nhận theo quy trình nào phải do nhân viên y tế đánh giá, không thể chọn chỉ dựa vào tên dịch vụ.
- **KH:** Sốt thôi thì có phải cấp cứu không?
- **NV:** Chỉ với thông tin sốt thì em không thể xác định mức độ khẩn cấp. Anh/chị cho em hỏi người nhà có khó thở, lơ mơ, co giật hoặc biểu hiện nguy hiểm nào không? Nếu có, cần liên hệ cấp cứu ngay.
- **KH:** Không, vẫn tỉnh táo, chỉ mệt.
- **NV:** Vậy anh/chị nên liên hệ cơ sở khám phù hợp để được hướng dẫn tiếp nhận sớm. Em có thể hỗ trợ tra cứu thông tin đăng ký, nhưng không thể xác nhận trước khu cách ly có tiếp nhận được không.
- **KH:** Ok, tôi hiểu rồi.

## CLI-005 — Chuẩn bị trước siêu âm ổ bụng

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008240` · Offering: `CLI-IMG-US-ABD` · Giá W12: **630.000 đồng/lần**

- **KH:** Mình định siêu âm bụng, có cần nhịn ăn từ tối hôm trước không bạn?
- **NV:** Việc chuẩn bị phụ thuộc mục đích siêu âm. Anh/chị nên hỏi trực tiếp nơi thực hiện để nhận hướng dẫn phù hợp, thay vì tự chuẩn bị theo suy đoán.
- **KH:** Vậy bên mình có siêu âm ổ bụng tổng quát không?
- **NV:** Có ạ, giá niêm yết của dịch vụ siêu âm ổ bụng tổng quát là 630.000 đồng/lần.
- **KH:** Tôi làm ca đêm nên khó sắp xếp.
- **NV:** Anh/chị cho em thời gian dự kiến, em sẽ hướng dẫn cách kiểm tra lịch và yêu cầu chuẩn bị. Em chưa thể xác nhận suất khám qua tin nhắn này.
- **KH:** Để tôi xem lịch làm rồi báo.

## CLI-006 — Người lớn tuổi muốn khám thần kinh

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008241` · Offering: `CLI-SP-NEURO` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Ba mình 68 tuổi, dạo này hay tê tay, muốn khám thần kinh thì đăng ký sao?
- **NV:** Dạ, phòng khám có dịch vụ khám Nội thần kinh, phí 1.800.000 đồng/lần. Ba mình bị tê tay lâu rồi hay mới xuất hiện đột ngột ạ?
- **KH:** Mấy tháng nay, lúc có lúc không.
- **NV:** Với tình trạng kéo dài, anh/chị nên đưa bác đến khám để bác sĩ đánh giá. Nếu xuất hiện đột ngột yếu một bên người, méo miệng hoặc nói khó thì cần gọi cấp cứu ngay, không chờ lịch khám thường.
- **KH:** Tôi có cần chụp MRI trước không?
- **NV:** Chưa nên tự đăng ký chụp chỉ vì có tê tay anh/chị ạ. Bác sĩ sẽ khám và quyết định có cần cận lâm sàng nào phù hợp.
- **KH:** Được, để tôi đưa ba đi khám trước.

## CLI-007 — Xét nghiệm máu có phát hiện mọi bệnh?

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008242` · Offering: `BLOODTEST` · Giá W12: **320.000 đồng/lượt**

- **KH:** Cho hỏi xét nghiệm máu tổng quát 320k là biết mình có bệnh gì luôn đúng không?
- **NV:** Dạ không hẳn ạ. Xét nghiệm máu cung cấp một số thông tin tùy danh mục chỉ số được thực hiện, không thể kết luận mọi bệnh chỉ từ một xét nghiệm.
- **KH:** Mình chỉ muốn kiểm tra sức khỏe vì dạo này hơi mệt.
- **NV:** Nếu đang có triệu chứng, anh/chị nên khám để bác sĩ đánh giá và tư vấn xét nghiệm phù hợp, thay vì tự chọn một xét nghiệm với kỳ vọng kiểm tra được tất cả.
- **KH:** Vậy 320k là tiền gì?
- **NV:** Giá niêm yết của dịch vụ xét nghiệm máu tổng quát là 320.000 đồng/lượt. Phạm vi chỉ số cụ thể cần được xác nhận trước khi thực hiện.
- **KH:** À hiểu, tôi hỏi thêm rồi quyết định.

## CLI-008 — Muốn đổi ngày khám phụ khoa

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008243` · Offering: `CLI-SP-OB` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Chị nhắn hôm qua muốn khám phụ khoa thứ sáu, giờ bận mất rồi, chuyển sang tuần sau được không em?
- **NV:** Dạ chị cho em biết ngày và khung giờ chị mong muốn trong tuần sau nhé. Em sẽ hướng dẫn cách kiểm tra; hiện chưa có lịch nào được xác nhận qua cuộc trò chuyện này ạ.
- **KH:** Chiều thứ ba được không?
- **NV:** Em ghi nhận nguyện vọng chiều thứ ba. Chị đợi xác nhận chính thức trước khi sắp xếp đến nhé.
- **KH:** Phí khám vẫn 1tr8 đúng không?
- **NV:** Dạ, giá khám Sản Phụ khoa trong danh mục hiện tại là 1.800.000 đồng/lần. Nếu có dịch vụ bổ sung sẽ được thông tin riêng.
- **KH:** Ừ, em kiểm tra giúp chị.

## CLI-009 — Lo sợ khi chụp MRI

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008244` · Offering: `CLI-IMG-MRI` · Giá W12: **4.500.000 đồng/lần**

- **KH:** Em ơi chụp MRI có phải nằm trong cái máy kín không? Chị rất sợ không gian hẹp.
- **NV:** Dạ em hiểu. Trước khi chụp, chị nên báo rõ việc mình sợ không gian kín để nhân viên chẩn đoán hình ảnh đánh giá và hướng dẫn phương án phù hợp.
- **KH:** Chị sợ tới đó lại không chụp được, mất tiền oan.
- **NV:** Chị nên hỏi trực tiếp phòng khám trước khi thực hiện, nhất là về quy trình và điều kiện thu phí. Em chưa thể khẳng định chị chắc chắn chụp được.
- **KH:** Bên mình giá bao nhiêu?
- **NV:** Dịch vụ chụp MRI hiện niêm yết 4.500.000 đồng/lần. Chị có chỉ định chụp từ bác sĩ chưa ạ?
- **KH:** Chưa, chị đang tìm hiểu thôi.
- **NV:** Vậy chị nên khám để bác sĩ đánh giá sự cần thiết trước, tránh đăng ký một kỹ thuật chưa chắc phù hợp.

## CLI-010 — Phí tư vấn ngắn có bao gồm xét nghiệm?

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008245` · Offering: `CLI-GEN-CONSULT-S` · Giá W12: **300.000 đồng/lần**

- **KH:** Tư vấn dưới 30 phút 300 nghìn, vậy khám xong xét nghiệm luôn trong đó hả em?
- **NV:** Dạ, 300.000 đồng/lần là giá của dịch vụ tư vấn dưới 30 phút. Em không muốn gộp phí tư vấn với xét nghiệm vì đây là các dịch vụ khác nhau.
- **KH:** Ủa vậy tôi phải đóng thêm hả?
- **NV:** Chỉ khi có dịch vụ bổ sung phù hợp và được thông tin, đồng ý thực hiện thì mới phát sinh khoản tương ứng. Em có thể giúp anh/chị kiểm tra bảng phí từng dịch vụ cụ thể.
- **KH:** Tôi chỉ muốn hỏi bác sĩ vài vấn đề thôi.
- **NV:** Vậy anh/chị có thể tìm hiểu dịch vụ tư vấn trước. Nếu vấn đề cần khám trực tiếp hoặc làm xét nghiệm, nhân viên chuyên môn sẽ hướng dẫn thêm.
- **KH:** Ok vậy rõ hơn rồi.

## CLI-011 — Bố nhắn dồn vì con sốt

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008246` · Offering: `CLI-SP-PEDI` · Giá W12: **1.800.000 đồng/lần**

- **KH:** em oi con toi sot cao, gio run run, ben minh kham nhi dc ko
- **NV:** Anh/chị cho em hỏi bé có co giật, khó thở, lơ mơ hoặc khó đánh thức không? Nếu có, cần gọi 115 hoặc đưa bé đến cơ sở cấp cứu ngay, không chờ đặt lịch qua Zalo.
- **KH:** bé đang run, tôi lo quá, có cần qua luôn không
- **NV:** Vì em không thể đánh giá mức độ nguy hiểm qua tin nhắn, anh/chị nên cho bé được nhân viên y tế đánh giá ngay. Nếu bé có dấu hiệu nguy hiểm hoặc tình trạng xấu đi, hãy gọi cấp cứu 115.
- **KH:** Tôi gọi người nhà đưa bé đi đây.
- **NV:** Vâng, anh/chị ưu tiên đưa bé đi đánh giá ngay nhé. Mình chưa cần trao đổi phí khám lúc này.

## CLI-012 — Không biết nên khám chuyên khoa nào

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008248` · Offering: `CLI-GEN-SPEC` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Mình đau bụng lúc có lúc không, lên mạng thấy đủ thứ bệnh. Đăng ký khám chuyên khoa nào vậy?
- **NV:** Em hiểu mình dễ lo khi đọc nhiều thông tin. Chỉ dựa vào mô tả qua chat, em không thể xác định chuyên khoa phù hợp hay nguyên nhân đau bụng.
- **KH:** Tôi sợ đăng ký sai rồi mất thêm tiền.
- **NV:** Anh/chị có thể liên hệ quầy tiếp nhận để được hướng dẫn đăng ký bước khám ban đầu. Nếu đau dữ dội, ngất, nôn ra máu hoặc có dấu hiệu nguy hiểm, cần đi cấp cứu ngay.
- **KH:** Bên mình khám chuyên khoa giá sao?
- **NV:** Danh mục khám bệnh chuyên khoa hiện có mức 1.800.000 đồng/lần. Em sẽ không tự chọn chuyên khoa thay bác sĩ; mình nên xác định hướng khám phù hợp trước khi đăng ký.
- **KH:** Vậy cho tôi hỏi quy trình đăng ký trước.

## CLI-013 — Khám tổng quát có phải gói đầy đủ?

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008249` · Offering: `GENCHECK` · Giá W12: **450.000 đồng/lượt**

- **KH:** Khám tổng quát 450k là khám hết các cơ quan luôn đúng không?
- **NV:** Dạ, 450.000 đồng/lượt là giá niêm yết của dịch vụ “Khám tổng quát”. Tên dịch vụ chưa đủ để khẳng định có bao gồm tất cả xét nghiệm hay chẩn đoán hình ảnh.
- **KH:** Tôi thấy chỗ khác bán gói mấy triệu nên hơi khó hiểu.
- **NV:** Đúng là các cơ sở có thể xây dựng danh mục khám khác nhau. Mình nên xem nội dung cụ thể được thực hiện rồi mới so sánh chi phí anh/chị ạ.
- **KH:** Vậy muốn khám định kỳ thì làm sao?
- **NV:** Anh/chị cho em biết mục đích khám định kỳ hay đang có triệu chứng để em hướng dẫn bước tiếp nhận phù hợp. Danh mục hạng mục chi tiết cần được phòng khám xác nhận trước khi anh/chị quyết định.
- **KH:** Tôi khám định kỳ thôi, để tôi hỏi thêm danh mục.

## CLI-014 — Khám nội có hẹn trước giờ làm

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008251` · Offering: `CLI-GEN-APPT` · Giá W12: **345.000 đồng/lần**

- **KH:** Mình đi làm 8h, muốn khám nội đa khoa có hẹn sớm nhất có thể. Có slot 7h không?
- **NV:** Em hiểu anh/chị cần sắp xếp trước giờ làm. Tuy nhiên em chưa có lịch trống hoặc giờ hoạt động đã xác nhận nên không thể hứa khung 7h ạ.
- **KH:** Thế khám có hẹn bao nhiêu tiền?
- **NV:** Phí khám nội đa khoa có hẹn hiện là 345.000 đồng/lần. Anh/chị cho em ngày mong muốn, em sẽ hướng dẫn cách kiểm tra lịch.
- **KH:** Sáng thứ tư tuần tới.
- **NV:** Dạ, em ghi nhận nhu cầu sáng thứ tư. Mình chỉ xem là yêu cầu đặt lịch, chưa phải lịch khám được xác nhận nhé.
- **KH:** Ok, chờ bên mình xác nhận.

## CLI-015 — Ngại bị tư vấn điều trị nha khoa

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008252` · Offering: `DENTAL` · Giá W12: **250.000 đồng/lượt**

- **KH:** Mình chỉ muốn khám răng thôi, không muốn vừa vào là bị tư vấn làm đủ thứ.
- **NV:** Dạ, anh/chị có thể đăng ký khám và tư vấn nha khoa trước để bác sĩ kiểm tra tình trạng răng miệng. Việc thực hiện điều trị thêm cần được giải thích rõ và có sự đồng ý của anh/chị.
- **KH:** Khám trước có tốn nhiều không?
- **NV:** Giá dịch vụ khám và tư vấn nha khoa trong danh mục hiện là 250.000 đồng/lượt.
- **KH:** Nếu bác sĩ nói phải làm thêm thì tôi được về suy nghĩ chứ?
- **NV:** Được trao đổi, đặt câu hỏi và cân nhắc trước khi đồng ý với các thủ thuật không khẩn cấp là quyền của anh/chị. Anh/chị có thể đề nghị giải thích lợi ích, rủi ro và chi phí dự kiến trước khi quyết định.
- **KH:** Ừ vậy tôi đi khám trước.

## CLI-016 — Đau răng muốn nhổ ngay

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008253` · Offering: `DENTAL` · Giá W12: **250.000 đồng/lượt**

- **KH:** răng tôi đau quá, tới là nhổ luôn được không?
- **NV:** Em hiểu anh/chị đang rất khó chịu. Việc có thể nhổ răng ngay hay cần xử trí khác phải do bác sĩ nha khoa khám và đánh giá, em không thể xác nhận trước.
- **KH:** Tôi đau mấy hôm rồi, không ngủ được.
- **NV:** Anh/chị nên được khám sớm. Nếu có sưng lan nhanh, khó thở hoặc khó nuốt, cần đến cơ sở cấp cứu ngay.
- **KH:** Khám bao nhiêu?
- **NV:** Khám và tư vấn nha khoa hiện là 250.000 đồng/lượt; chi phí điều trị nếu có là khoản cần xác định riêng.
- **KH:** Ok, tôi hỏi để sắp xếp đi.

## CLI-017 — Khách thắc mắc khám răng và điều trị

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008254` · Offering: `DENTAL` · Giá W12: **250.000 đồng/lượt**

- **KH:** 250 nghìn là khám răng rồi lấy cao răng luôn đúng không?
- **NV:** Dạ, 250.000 đồng/lượt là giá của “Khám và tư vấn nha khoa”. Em chưa có cơ sở để nói giá này đã bao gồm lấy cao răng ạ.
- **KH:** Vậy sao không ghi rõ, khách dễ hiểu lầm lắm.
- **NV:** Anh/chị góp ý đúng, cách hiển thị giá cần làm rõ phần khám và phần thủ thuật. Em xin ghi nhận phản ánh để phòng khám rà soát thông tin.
- **KH:** Tôi chỉ muốn biết trước tổng tiền thôi.
- **NV:** Để có tổng tiền chính xác, mình cần biết dịch vụ cụ thể sau khi được khám và tư vấn. Em sẽ hướng dẫn anh/chị hỏi bảng phí tương ứng trước khi đồng ý thực hiện.
- **KH:** Được, cảm ơn.

## CLI-018 — So sánh khám tổng quát với khám nội

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008256` · Offering: `GENCHECK` · Giá W12: **450.000 đồng/lượt**

- **KH:** Cho hỏi khám tổng quát 450k mà khám nội có hẹn 345k, hai cái này khác nhau gì?
- **NV:** Đây là hai dịch vụ riêng: “Khám tổng quát” 450.000 đồng/lượt và “Khám nội đa khoa có hẹn” 345.000 đồng/lần. Em chưa thể suy ra toàn bộ hạng mục từ tên gọi.
- **KH:** Mình muốn kiểm tra sức khỏe định kỳ, không có triệu chứng gì.
- **NV:** Anh/chị nên hỏi rõ nội dung của khám tổng quát trước, rồi chọn theo nhu cầu. Em có thể hướng dẫn liên hệ quầy tiếp nhận để xác nhận nội dung và chi phí.
- **KH:** Không phải cứ 450k là xét nghiệm máu luôn à?
- **NV:** Dạ, không nên mặc định như vậy. Xét nghiệm là nhóm dịch vụ có mã và giá riêng; nếu có trong gói nào thì phải được xác nhận rõ bằng danh mục.
- **KH:** Ừ, tôi hỏi lại cho chắc.

## CLI-019 — Khám không hẹn, sợ chờ lâu

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008259` · Offering: `CLI-GEN-WALKIN` · Giá W12: **550.000 đồng/lần**

- **KH:** Nếu chiều nay tôi ghé khám nội không hẹn thì có phải đợi mấy tiếng không?
- **NV:** Dạ, em chưa có số lượng bệnh nhân đang chờ và thời gian tiếp nhận thực tế nên không thể báo chính xác thời gian chờ.
- **KH:** Tôi đang tranh thủ giờ nghỉ, đợi lâu là phải về.
- **NV:** Em hiểu ạ. Dịch vụ khám nội đa khoa không hẹn hiện là 550.000 đồng/lần. Nếu anh/chị cần chủ động thời gian, có thể cân nhắc hỏi quầy tiếp nhận về lựa chọn khám có hẹn.
- **KH:** Có hẹn giá khác à?
- **NV:** Dạ, khám nội đa khoa có hẹn niêm yết 345.000 đồng/lần. Đây là hai dịch vụ khác nhau; anh/chị nên kiểm tra điều kiện tiếp nhận và lịch thực tế trước khi đến.
- **KH:** Để tôi gọi hỏi trực tiếp vậy.

## CLI-020 — Người nhà hỏi khám tim mạch

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008260` · Offering: `CLI-SP-CARDIO` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Mẹ mình thỉnh thoảng hồi hộp, muốn khám tim mạch. Bên bạn có không?
- **NV:** Dạ có dịch vụ khám Nội tim mạch, giá 1.800.000 đồng/lần. Hiện bác có đau ngực, khó thở nhiều, ngất hoặc triệu chứng cấp tính nào không ạ?
- **KH:** Không, chỉ thỉnh thoảng tim đập nhanh rồi hết.
- **NV:** Anh/chị nên sắp xếp cho bác được bác sĩ đánh giá. Nếu xuất hiện đau ngực dữ dội, khó thở nghiêm trọng hoặc ngất, cần gọi cấp cứu ngay thay vì chờ lịch khám.
- **KH:** Có cần làm điện tim trước không?
- **NV:** Việc có cần điện tim hoặc xét nghiệm khác phải do bác sĩ đánh giá. Em không tự chỉ định được, và giá khám chưa có nghĩa đã bao gồm các kỹ thuật đó.
- **KH:** Rồi, để mình trao đổi với mẹ.

## CLI-021 — Khách phàn nàn được hướng dẫn lòng vòng

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008262` · Offering: `CLI-GEN-SPEC` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Hôm trước tôi hỏi khám chuyên khoa, người này bảo hỏi người kia. Giờ tôi phải làm sao mới đăng ký được?
- **NV:** Em xin lỗi vì anh/chị phải hỏi nhiều lần mà vẫn chưa có hướng dẫn rõ ràng. Em xin phép làm rõ nhu cầu để hướng dẫn đúng đầu mối, không để anh/chị phải kể lại nhiều lần.
- **KH:** Tôi muốn khám chuyên khoa nhưng chưa biết chuyên khoa nào.
- **NV:** Mình cần quầy tiếp nhận hướng dẫn bước khám phù hợp trước. Em không thể tự quyết định chuyên khoa chỉ từ vài dòng mô tả.
- **KH:** Tôi chỉ cần biết giá trước thôi.
- **NV:** Phí “Khám bệnh chuyên khoa” hiện là 1.800.000 đồng/lần. Em sẽ ghi nhận yêu cầu làm rõ quy trình đăng ký để phòng khám phản hồi cụ thể.
- **KH:** Được, mong lần này rõ ràng hơn.

## CLI-022 — Đưa người cao tuổi đi khám không hẹn

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008265` · Offering: `CLI-GEN-WALKIN` · Giá W12: **550.000 đồng/lần**

- **KH:** Mai tôi đưa ông ngoại đi khám nội, ông đi lại khó. Có cần người nhà đi cùng không?
- **NV:** Dạ, người nhà có thể hỗ trợ bác khi làm thủ tục. Anh/chị nên báo trước nhu cầu hỗ trợ di chuyển với quầy tiếp nhận để được hướng dẫn phù hợp.
- **KH:** Ông không đứng chờ lâu được, có ưu tiên không?
- **NV:** Em không muốn hứa có làn ưu tiên khi chưa xác nhận quy trình thực tế. Mình nên trao đổi trước với quầy tiếp nhận về tình trạng đi lại của bác.
- **KH:** Khám không hẹn giá bao nhiêu?
- **NV:** Giá khám nội đa khoa không hẹn là 550.000 đồng/lần. Nếu muốn chủ động thời gian hơn, anh/chị có thể hỏi thêm lựa chọn khám có hẹn và lịch thực tế.
- **KH:** Ok, để tôi chuẩn bị cho ông.

## CLI-023 — Hỏi cách lấy mẫu nước tiểu

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008266` · Offering: `CLI-LAB-URINE` · Giá W12: **150.000 đồng/lần**

- **KH:** Xét nghiệm nước tiểu 150k, tôi lấy mẫu ở nhà mang tới được không?
- **NV:** Dạ, giá xét nghiệm nước tiểu hiện là 150.000 đồng/lần. Về cách lấy, bảo quản và thời gian chuyển mẫu, anh/chị cần làm theo hướng dẫn chính thức khi được tiếp nhận.
- **KH:** Tôi thấy trên mạng nói lấy mẫu buổi sáng, đúng không?
- **NV:** Tùy loại xét nghiệm và mục đích kiểm tra sẽ có yêu cầu khác nhau. Em không muốn hướng dẫn chung khiến anh/chị phải lấy lại mẫu.
- **KH:** Vậy tới nơi hỏi rồi lấy hả?
- **NV:** Anh/chị nên liên hệ khu xét nghiệm trước để nhận hướng dẫn chính thức, đồng thời xác nhận điều kiện tiếp nhận mẫu.
- **KH:** Được, vậy tôi chưa lấy trước.

## CLI-024 — Khám tổng quát cuối tuần

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008267` · Offering: `GENCHECK` · Giá W12: **450.000 đồng/lượt**

- **KH:** Bên mình thứ bảy có khám tổng quát không? Tôi chỉ rảnh cuối tuần.
- **NV:** Dạ, em chưa có lịch hoạt động cuối tuần đã xác nhận để báo chính xác. Anh/chị cho em biết thứ bảy nào muốn đi, em sẽ hướng dẫn cách kiểm tra.
- **KH:** Tuần này. Giá 450k đúng không?
- **NV:** Dạ, giá niêm yết “Khám tổng quát” là 450.000 đồng/lượt. Danh mục hạng mục khám và thời gian tiếp nhận cần được xác nhận riêng ạ.
- **KH:** Tôi cần biết sớm để xin nghỉ làm bù.
- **NV:** Em hiểu. Anh/chị nên nhận xác nhận lịch trước khi sắp xếp công việc; em không muốn báo chắc rồi làm lỡ kế hoạch của mình.
- **KH:** Ừ, chờ xác nhận vậy.

## CLI-025 — Hai giá xét nghiệm máu khác nhau

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008268` · Offering: `BLOODTEST` · Giá W12: **320.000 đồng/lượt**

- **KH:** Sao tôi thấy xét nghiệm máu tổng quát 320k, chỗ khác trong bảng là CBC 280k? Bên mình đăng nhầm à?
- **NV:** Anh/chị đang xem hai dịch vụ khác nhau: xét nghiệm máu tổng quát giá 320.000 đồng/lượt và xét nghiệm máu tổng quát CBC giá 280.000 đồng/lần.
- **KH:** Nhưng tên đều là xét nghiệm máu tổng quát mà?
- **NV:** Đúng là tên hiển thị rất gần nhau nên dễ gây nhầm. Em chưa có bảng chỉ số để khẳng định hai dịch vụ khác nhau cụ thể ở đâu.
- **KH:** Vậy tôi phải chọn cái nào?
- **NV:** Mình nên hỏi khu xét nghiệm về phạm vi chỉ số và nhu cầu kiểm tra, hoặc để bác sĩ tư vấn khi có triệu chứng. Em sẽ không tự chọn thay anh/chị.
- **KH:** Ok, cần làm rõ tên cho khách thật.

## CLI-026 — Lưu viện ngắn theo nhu cầu

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008269` · Offering: `CLI-INP-DAY-S` · Giá W12: **920.000 đồng/lần**

- **KH:** Cho hỏi tôi có thể đăng ký nằm theo dõi 2 tiếng cho yên tâm không?
- **NV:** Phòng khám có dịch vụ lưu viện trong ngày 1–4 giờ, giá 920.000 đồng/lần. Tuy nhiên việc lưu viện phải dựa trên đánh giá và quyết định chuyên môn, không phải dịch vụ có thể tự đặt chỉ để nghỉ ngơi.
- **KH:** Tôi mới làm thủ thuật ở nơi khác, hơi lo.
- **NV:** Anh/chị nên liên hệ nơi đã thực hiện thủ thuật hoặc cơ sở y tế phù hợp để được đánh giá và hướng dẫn theo dõi. Nếu có chảy máu nhiều, khó thở, ngất hay triệu chứng nguy hiểm, cần đi cấp cứu ngay.
- **KH:** Vậy tôi không đặt trước được hả?
- **NV:** Em có thể hướng dẫn liên hệ quầy tiếp nhận để hỏi quy trình, nhưng không thể xác nhận chỉ định lưu viện hoặc chỗ trống qua chat.
- **KH:** Tôi hiểu rồi.

## CLI-027 — Cấp cứu không chờ Zalo

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008271` · Offering: `CLI-EMG-ISOLATION` · Giá W12: **1.100.000 đồng/lần**

- **KH:** Người nhà tôi đang khó thở, tôi nhắn hỏi có cần đặt trước khu cấp cứu cách ly không?
- **NV:** Không nên chờ đặt lịch qua Zalo anh/chị nhé. Nếu người nhà đang khó thở nặng, hãy gọi 115 hoặc đến cơ sở cấp cứu phù hợp ngay.
- **KH:** Tôi thấy giá bên mình 1tr1, sợ tới lại không nhận.
- **NV:** 1.100.000 đồng/lần là giá niêm yết của dịch vụ “Khám cấp cứu cách ly”, không phải xác nhận rằng cơ sở đang có khả năng tiếp nhận ca này. Việc cấp cứu cần ưu tiên ngay, đừng để câu hỏi về giá làm chậm việc tìm trợ giúp.
- **KH:** Được, tôi gọi xe cấp cứu.
- **NV:** Vâng anh/chị, mình ưu tiên liên hệ cấp cứu ngay ạ.

## CLI-028 — Khách cân nhắc phí khám chuyên khoa

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008272` · Offering: `CLI-GEN-SPEC` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Khám chuyên khoa 1 triệu 8 có hơi cao không em? Tôi chỉ muốn hỏi bác sĩ chút thôi.
- **NV:** Dạ, mức niêm yết của dịch vụ khám bệnh chuyên khoa là 1.800.000 đồng/lần. Em hiểu anh/chị muốn cân nhắc chi phí trước khi quyết định.
- **KH:** Có cách nào hỏi nhanh mà không mất 1tr8 không?
- **NV:** Danh mục còn có phí tư vấn dưới 30 phút là 300.000 đồng/lần, nhưng đây là dịch vụ khác; em không thể khẳng định nó thay thế được khám chuyên khoa.
- **KH:** Vậy cái nào hợp với tôi?
- **NV:** Anh/chị có thể nói ngắn gọn mục đích muốn trao đổi để quầy tiếp nhận hướng dẫn loại dịch vụ phù hợp. Nếu có triệu chứng cần thăm khám, mình không nên chọn chỉ theo giá.
- **KH:** Ừ, tôi sẽ hỏi rõ trước.

## CLI-029 — Có thiết bị cấy ghép, muốn chụp MRI

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008273` · Offering: `CLI-IMG-MRI` · Giá W12: **4.500.000 đồng/lần**

- **KH:** Mình có đặt thiết bị kim loại trong người từ trước, giờ muốn chụp MRI có sao không?
- **NV:** Anh/chị cần khai báo rõ loại thiết bị và thông tin cấy ghép với bác sĩ, nhân viên chẩn đoán hình ảnh trước khi chụp. An toàn MRI phải được kiểm tra chuyên môn, em không thể kết luận qua Zalo.
- **KH:** Tôi không nhớ tên thiết bị, chỉ có giấy ra viện cũ.
- **NV:** Anh/chị nên giữ giấy tờ liên quan và trao đổi qua kênh tiếp nhận y tế phù hợp, không cần gửi toàn bộ hồ sơ nhạy cảm trong cuộc chat này.
- **KH:** Chụp MRI bên mình giá bao nhiêu?
- **NV:** Giá niêm yết chụp MRI là 4.500.000 đồng/lần. Nhưng mình cần được đánh giá chỉ định và an toàn trước khi quyết định thực hiện.
- **KH:** Ok, tôi sẽ tìm lại giấy tờ.

## CLI-030 — Đổi ý về chuyên khoa

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008274` · Offering: `CLI-GEN-SPEC` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Hôm qua tôi định khám tiêu hóa, nay người nhà bảo nên khám thần kinh. Bên bạn đổi giúp được không?
- **NV:** Dạ, em có thể ghi nhận việc anh/chị muốn thay đổi nhu cầu đăng ký. Nhưng chọn chuyên khoa phù hợp cần dựa trên triệu chứng và đánh giá của nhân viên y tế, không chỉ theo lời khuyên của người quen.
- **KH:** Tôi chưa có lịch xác nhận đâu, chỉ hỏi trước.
- **NV:** Vậy mình chưa cần đổi lịch nào cả. Anh/chị có thể trao đổi với quầy tiếp nhận để được hướng dẫn bước khám ban đầu phù hợp.
- **KH:** Giá khám chuyên khoa có khác nhau không?
- **NV:** Mã “Khám bệnh chuyên khoa” đang niêm yết 1.800.000 đồng/lần. Một số chuyên khoa cụ thể cũng có mã riêng, nên cần xác nhận chính xác dịch vụ trước khi báo phí.
- **KH:** Ừ, để tôi nói rõ tình trạng khi đăng ký.

## CLI-031 — Tư vấn trên 30 phút khác gì dưới 30 phút

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008277` · Offering: `CLI-GEN-CONSULT-L` · Giá W12: **600.000 đồng/lần**

- **KH:** Tôi thấy tư vấn trên 30 phút 600k, dưới 30 phút 300k. Tôi nói chuyện lâu quá thì tự động tính thêm à?
- **NV:** Hai dịch vụ đang niêm yết lần lượt 600.000 đồng/lần và 300.000 đồng/lần. Cách áp dụng khi thời lượng thay đổi cần được phòng khám xác nhận; em không tự khẳng định có tự động tính thêm.
- **KH:** Tôi sợ đang tư vấn thì bị tính tiền bất ngờ.
- **NV:** Em hiểu. Anh/chị nên yêu cầu làm rõ loại tư vấn, thời lượng và nguyên tắc tính phí trước khi bắt đầu để chủ động quyết định.
- **KH:** Tôi có thể hỏi trước khi đăng ký không?
- **NV:** Dạ được, em sẽ hướng dẫn anh/chị liên hệ đầu mối tiếp nhận để xác nhận chính sách. Mình không nên chốt mức phí phát sinh chỉ dựa vào suy đoán.
- **KH:** Được, vậy tôi yên tâm hơn.

## CLI-032 — Bé ho kéo dài, mẹ hỏi có cần chụp phim

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008278` · Offering: `CLI-SP-PEDI` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Bé nhà chị ho gần tuần rồi, có cần chụp X-quang luôn không em?
- **NV:** Dạ, chị nên cho bé được bác sĩ chuyên khoa Nhi thăm khám để đánh giá nguyên nhân. Em không thể chỉ định chụp hay xét nghiệm qua tin nhắn.
- **KH:** Chị sợ đưa đi rồi bác sĩ lại bảo làm đủ thứ.
- **NV:** Em hiểu lo lắng về chi phí và việc bé phải làm thủ thuật. Chị có thể đề nghị bác sĩ giải thích lý do, lợi ích và chi phí dự kiến của từng chỉ định trước khi quyết định.
- **KH:** Khám nhi bao nhiêu?
- **NV:** Phí khám Nhi khoa là 1.800.000 đồng/lần. Nếu bé khó thở, tím tái, li bì hoặc có dấu hiệu nguy hiểm, chị cần đưa bé đi cấp cứu ngay, không chờ lịch khám thông thường.
- **KH:** Ừ, chị đưa bé đi khám trước.

## CLI-033 — Sợ đau khi khám nha khoa

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008279` · Offering: `DENTAL` · Giá W12: **250.000 đồng/lượt**

- **KH:** Tôi sợ nha sĩ lắm, lần trước làm răng đau nên giờ ngại đi khám.
- **NV:** Dạ, anh/chị cứ nói rõ trải nghiệm trước đây với bác sĩ khi đến khám. Bác sĩ cần đánh giá tình trạng và trao đổi phương án phù hợp, không nên hứa trước rằng mọi thủ thuật đều không đau.
- **KH:** Tôi chỉ muốn xem cái răng sâu tới đâu thôi.
- **NV:** Vậy trước tiên anh/chị có thể tìm hiểu dịch vụ khám và tư vấn nha khoa, giá 250.000 đồng/lượt.
- **KH:** Nếu cần trám thì báo tiền trước nhé.
- **NV:** Anh/chị nên yêu cầu giải thích chỉ định và chi phí dự kiến trước khi đồng ý điều trị. Em chưa có bảng giá trám răng được xác minh nên không báo số tiền cụ thể ở đây.
- **KH:** Ok, như vậy được.

## CLI-034 — Khách tự chọn xét nghiệm chức năng thận

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008281` · Offering: `CLI-LAB-KIDNEY` · Giá W12: **420.000 đồng/lần**

- **KH:** Tôi đọc trên mạng thấy hay mệt là do thận, cho tôi làm xét nghiệm chức năng thận luôn.
- **NV:** Dạ, xét nghiệm chức năng thận có trong danh mục, giá 420.000 đồng/lần. Tuy nhiên mệt mỏi có nhiều nguyên nhân, em không thể kết luận liên quan đến thận hay chỉ định xét nghiệm từ thông tin này.
- **KH:** Tôi chỉ muốn làm cho chắc.
- **NV:** Anh/chị nên được bác sĩ đánh giá để chọn xét nghiệm phù hợp với tình trạng sức khỏe. Nếu vẫn muốn tìm hiểu dịch vụ, em có thể hỗ trợ thông tin hành chính và giá.
- **KH:** Có phải nhịn ăn không?
- **NV:** Yêu cầu chuẩn bị tùy chỉ số và hướng dẫn xét nghiệm cụ thể; anh/chị cần xác nhận trước khi đến, không tự nhịn ăn theo lời truyền miệng.
- **KH:** Rồi, tôi hỏi bác sĩ trước.

## CLI-035 — Tình huống cấp cứu đau ngực

- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000008283` · Offering: `CLI-EMG-STANDARD` · Giá W12: **900.000 đồng/lần**

- **KH:** Anh tôi đang đau ngực dữ dội, vã mồ hôi, bên mình khám cấp cứu bao nhiêu?
- **NV:** Anh/chị gọi 115 ngay hoặc đưa anh đến cơ sở cấp cứu phù hợp gần nhất. Đau ngực dữ dội kèm vã mồ hôi là tình huống cần được đánh giá cấp cứu, không nên chờ báo giá qua Zalo.
- **KH:** Tôi sợ bên đó không nhận, phải gọi hỏi trước chứ?
- **NV:** Anh/chị hãy gọi cấp cứu ngay để được điều phối phù hợp. Em không có thông tin trực cấp cứu thời điểm này và không thể xác nhận khả năng tiếp nhận của phòng khám qua tin nhắn.
- **KH:** Tôi gọi rồi, họ đang hướng dẫn.
- **NV:** Vâng, anh/chị làm theo hướng dẫn của tổng đài cấp cứu nhé.

## CLI-036 — Lead hỏi thời gian trả kết quả thận

- Split: **dev** · Link: `LEAD` `000f5347-f417-bd5c-a81d-b01e624e7ee9` · Offering: `CLI-LAB-KIDNEY` · Giá W12: **420.000 đồng/lần**

- **KH:** Xét nghiệm chức năng thận 420k đúng không? Có kết quả trong một tiếng không?
- **NV:** Dạ, giá niêm yết là 420.000 đồng/lần. Em chưa có thời gian trả kết quả được xác nhận nên không hứa một tiếng với anh/chị.
- **KH:** Tôi cần kết quả để mang đi khám nơi khác.
- **NV:** Vậy anh/chị nên liên hệ khu xét nghiệm để hỏi thời gian thực tế, loại mẫu và điều kiện tiếp nhận trước khi đến.
- **KH:** Có thể gửi kết quả qua Zalo luôn không?
- **NV:** Kết quả xét nghiệm là thông tin sức khỏe riêng tư; cách trả kết quả cần theo quy trình bảo mật của cơ sở. Em không thể cam kết gửi qua Zalo khi chưa xác nhận kênh được phép.
- **KH:** Ok, để tôi gọi hỏi trước.

## CLI-037 — Phòng lưu viện VIP còn chỗ không?

- Split: **dev** · Link: `LEAD` `00172a2c-9f48-765a-86b9-f37a59021d0c` · Offering: `CLI-INP-VIP` · Giá W12: **5.000.000 đồng/ngày**

- **KH:** Phòng lưu viện VIP 5 triệu/ngày hả? Tôi muốn đặt trước cho mẹ.
- **NV:** Dạ, mức niêm yết của dịch vụ lưu viện phòng VIP 24 giờ là 5.000.000 đồng/ngày. Việc có cần lưu viện, loại phòng phù hợp và khả năng bố trí phải được xác nhận theo đánh giá chuyên môn và tình trạng thực tế.
- **KH:** Tôi muốn phòng riêng cho mẹ nghỉ ngơi, không có bệnh gì nặng.
- **NV:** Em hiểu anh/chị muốn mẹ được thoải mái. Nhưng lưu viện là hoạt động y tế, không nên xem như đặt phòng nghỉ. Mình cần hỏi quầy tiếp nhận về điều kiện sử dụng dịch vụ.
- **KH:** Vậy chưa giữ phòng được à?
- **NV:** Dạ, em chưa thể giữ hoặc xác nhận phòng trống qua cuộc chat này.
- **KH:** Thôi để tôi hỏi bác sĩ của mẹ trước.

## CLI-038 — Xét nghiệm nước tiểu có phải đến sớm?

- Split: **dev** · Link: `LEAD` `002201aa-998b-255e-b9bc-5ff1d48af2c9` · Offering: `CLI-LAB-URINE` · Giá W12: **150.000 đồng/lần**

- **KH:** Mình làm xét nghiệm nước tiểu thì mấy giờ tới được?
- **NV:** Dạ, giá xét nghiệm nước tiểu là 150.000 đồng/lần. Em chưa có giờ tiếp nhận mẫu đã xác minh nên không muốn báo một khung giờ sai.
- **KH:** Tôi đi làm cả ngày, muốn tranh thủ.
- **NV:** Anh/chị có thể hỏi khu xét nghiệm về giờ nhận mẫu và cách chuẩn bị trước. Nếu được hướng dẫn lấy mẫu tại nhà, mình cần tuân thủ đúng quy cách bảo quản.
- **KH:** Có cần bác sĩ chỉ định không?
- **NV:** Điều kiện tiếp nhận xét nghiệm cần được cơ sở xác nhận; nếu đang có triệu chứng, anh/chị nên trao đổi với bác sĩ để chọn xét nghiệm phù hợp.
- **KH:** Rồi, tôi sẽ hỏi lịch.

## CLI-039 — Chụp X-quang có cần giấy chỉ định?

- Split: **dev** · Link: `LEAD` `004563dc-a211-a359-9228-08ac24ccb778` · Offering: `XRAY` · Giá W12: **280.000 đồng/lượt**

- **KH:** Bên bạn chụp X-quang 280k, tôi tự tới chụp được không?
- **NV:** Dạ, giá 280.000 đồng/lượt là của dịch vụ chụp X-quang. Việc thực hiện cần được kiểm tra chỉ định và điều kiện tiếp nhận phù hợp, không nên tự chụp chỉ để kiểm tra cho yên tâm.
- **KH:** Tôi chỉ bị đau lưng, muốn chụp xem sao.
- **NV:** Anh/chị nên khám để bác sĩ đánh giá có cần chẩn đoán hình ảnh không và cần chụp vùng nào.
- **KH:** Tôi thấy có chụp X-quang ngực 320k nữa?
- **NV:** Đúng ạ, đó là dịch vụ riêng “Chụp X-quang ngực thẳng”, giá 320.000 đồng/lần. Hai tên dịch vụ không nên coi là tương đương khi chưa xác nhận phạm vi.
- **KH:** Ok, tôi đi khám trước.

## CLI-040 — Người nhà muốn đăng ký tim mạch

- Split: **dev** · Link: `LEAD` `004927d8-5bcd-d456-8f9d-9bd7938955bd` · Offering: `CLI-SP-CARDIO` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Mình muốn đăng ký khám tim mạch cho bố. Bố lớn tuổi, tôi nhắn thay được không?
- **NV:** Anh/chị có thể hỏi thông tin hành chính và giá thay bố. Khi làm thủ tục, cơ sở sẽ cần xác minh thông tin và sự đồng ý phù hợp của người bệnh theo quy trình.
- **KH:** Bố tôi không dùng điện thoại thông minh.
- **NV:** Không sao ạ, anh/chị có thể hỗ trợ bố liên hệ quầy tiếp nhận. Em không cần anh/chị gửi hồ sơ bệnh án của bố qua Zalo lúc này.
- **KH:** Giá khám tim mạch bao nhiêu?
- **NV:** Khám Nội tim mạch hiện niêm yết 1.800.000 đồng/lần. Nếu bố đang đau ngực, khó thở nặng hoặc ngất thì cần cấp cứu ngay, không chờ đăng ký khám thường.
- **KH:** Không có, tôi chỉ muốn đặt khám định kỳ.

## CLI-041 — Hai mức giá X-quang

- Split: **test** · Link: `LEAD` `005c3331-45fd-775d-8458-25528bc9fa47` · Offering: `XRAY` · Giá W12: **280.000 đồng/lượt**

- **KH:** Sao bên mình có X-quang 280 nghìn mà X-quang ngực lại 320 nghìn? Tôi sợ đến nơi bị tính khác.
- **NV:** Anh/chị hỏi rất đúng. Trong danh mục có hai mã riêng: XRAY 280.000 đồng/lượt và CLI-IMG-XRAY “Chụp X-quang ngực thẳng” 320.000 đồng/lần.
- **KH:** Vậy nếu chụp phổi thì chắc chắn 320k à?
- **NV:** Em không thể tự kết luận kỹ thuật cần thực hiện chỉ từ cách gọi “chụp phổi”. Mình cần xác nhận chỉ định và dịch vụ phù hợp trước khi thực hiện.
- **KH:** Tôi muốn có giá rõ từ đầu.
- **NV:** Dạ, anh/chị nên yêu cầu đối chiếu đúng mã kỹ thuật trên phiếu chỉ định và bảng phí, tránh nhầm giữa hai dịch vụ.
- **KH:** Ừ, giải thích vậy dễ hiểu hơn.

## CLI-042 — Phụ huynh bực vì phải khai lại thông tin

- Split: **test** · Link: `LEAD` `00ac96b3-dfb4-b15e-969c-5371e7332736` · Offering: `CLI-SP-PEDI` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Tôi đã nói tuổi của bé với bên bạn hai lần rồi, giờ lại hỏi nữa. Làm việc gì kỳ vậy?
- **NV:** Em xin lỗi vì anh/chị phải cung cấp thông tin lặp lại. Em hiểu điều này rất mất thời gian, nhất là khi mình đang lo cho bé.
- **KH:** Tôi chỉ cần biết khám nhi bao nhiêu, có phải gửi bệnh án qua đây không?
- **NV:** Phí khám Nhi khoa là 1.800.000 đồng/lần. Anh/chị không cần gửi bệnh án hay hình ảnh sức khỏe nhạy cảm qua cuộc chat này chỉ để hỏi giá.
- **KH:** Vậy được rồi, lần sau đừng bắt khách kể hết.
- **NV:** Dạ, em ghi nhận góp ý về cách tiếp nhận. Những thông tin cần cho thăm khám nên được trao đổi qua kênh phù hợp với nhân viên y tế.
- **KH:** Ừ, cảm ơn.

## CLI-043 — Muốn gửi ảnh kết quả xét nghiệm

- Split: **test** · Link: `LEAD` `00e475d3-15c8-c05c-8bfd-c21c96977bf8` · Offering: `CLI-LAB-KIDNEY` · Giá W12: **420.000 đồng/lần**

- **KH:** Tôi có kết quả xét nghiệm thận, gửi ảnh lên đây bạn đọc giúp có nguy hiểm không?
- **NV:** Anh/chị đừng gửi thông tin sức khỏe riêng tư vào cuộc chat này nếu chưa có kênh tiếp nhận được xác nhận. Việc đọc và giải thích kết quả cần bác sĩ đánh giá cùng triệu chứng và tiền sử.
- **KH:** Nhưng tôi chỉ muốn biết chỉ số đỏ là sao thôi.
- **NV:** Em hiểu mình lo khi thấy chỉ số được đánh dấu. Tuy nhiên một giá trị ngoài khoảng tham chiếu chưa đủ để kết luận bệnh; em không nên diễn giải thay bác sĩ.
- **KH:** Nếu muốn làm lại xét nghiệm thì bao nhiêu?
- **NV:** Giá xét nghiệm chức năng thận hiện là 420.000 đồng/lần. Việc có cần làm lại hay không nên hỏi bác sĩ điều trị hoặc bác sĩ khám.
- **KH:** Ok, tôi sẽ hỏi bác sĩ.

## CLI-044 — Bảo hiểm có thanh toán không?

- Split: **test** · Link: `LEAD` `012108bc-ee2a-9d57-b4c3-5e9b6b3b2100` · Offering: `CLI-GEN-SPEC` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Khám chuyên khoa 1tr8 bên mình có bảo hiểm chi trả không?
- **NV:** Dạ, giá niêm yết của dịch vụ khám bệnh chuyên khoa là 1.800.000 đồng/lần. Về bảo hiểm, em chưa có thông tin hợp đồng và điều kiện thanh toán đã xác nhận nên không thể hứa mức được chi trả.
- **KH:** Tôi có bảo hiểm công ty, chắc được chứ?
- **NV:** Quyền lợi phụ thuộc hợp đồng, cơ sở khám và hồ sơ thực tế. Anh/chị nên kiểm tra với đơn vị bảo hiểm và phòng khám trước khi đăng ký.
- **KH:** Tôi không muốn đến rồi mới biết phải trả hết.
- **NV:** Em hiểu. Mình nên làm rõ điều kiện bảo lãnh hoặc hoàn phí trước khi đi, thay vì dựa vào suy đoán.
- **KH:** Để tôi liên hệ bảo hiểm trước.

## CLI-045 — Muốn khám nha khoa nhưng chưa điều trị

- Split: **test** · Link: `LEAD` `018d3e36-dc55-c554-bb9b-991b62ba9a62` · Offering: `DENTAL` · Giá W12: **250.000 đồng/lượt**

- **KH:** Tôi chỉ khám răng, không làm gì thêm trong hôm đó được không?
- **NV:** Dạ, anh/chị có thể trao đổi rõ mong muốn chỉ khám và tư vấn trước. Nếu bác sĩ đề xuất điều trị, mình có quyền hỏi kỹ và cân nhắc trước khi đồng ý với thủ thuật không khẩn cấp.
- **KH:** Tôi ngại bị ép làm luôn.
- **NV:** Em hiểu. Khi khám, anh/chị cứ nói rõ muốn biết tình trạng, các lựa chọn và chi phí dự kiến trước.
- **KH:** Khám phí bao nhiêu?
- **NV:** Khám và tư vấn nha khoa hiện là 250.000 đồng/lượt. Phí này không nên mặc định bao gồm thủ thuật khác khi chưa có danh mục xác nhận.
- **KH:** Vậy tôi đi khám trước rồi tính.

## CLI-046 — Phàn nàn chi phí khám tổng quát

- Split: **test** · Link: `LEAD` `01bf0290-2f9c-ee54-8447-28782d146a1a` · Offering: `GENCHECK` · Giá W12: **450.000 đồng/lượt**

- **KH:** Tôi hỏi khám tổng quát 450k, nhưng người khác lại nói còn xét nghiệm nữa. Sao tư vấn không thống nhất?
- **NV:** Em xin lỗi vì thông tin khiến anh/chị hiểu khác nhau. Mức 450.000 đồng/lượt là giá niêm yết của mã “Khám tổng quát”, chưa đủ để kết luận tất cả hạng mục nào đã bao gồm.
- **KH:** Khách cần biết tổng tiền trước khi đi chứ.
- **NV:** Dạ đúng, anh/chị cần được giải thích rõ nội dung thực hiện và chi phí dự kiến. Em sẽ ghi nhận phản ánh về cách tư vấn giá để phòng khám rà soát.
- **KH:** Tôi không muốn phát sinh mà không ai nói trước.
- **NV:** Anh/chị nên yêu cầu bảng hạng mục và giá của các dịch vụ được đề xuất trước khi đồng ý thực hiện. Em không tự đưa ra tổng tiền khi chưa có danh mục cụ thể.
- **KH:** Ừ, mong bên mình cải thiện.

## CLI-047 — Đau khớp có cần chụp phim?

- Split: **test** · Link: `LEAD` `01e67a12-d82e-8256-ba19-e4d259f24afb` · Offering: `CLI-SP-ORTHO` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Đầu gối tôi đau hơn tháng rồi, có cần chụp MRI hay X-quang trước khi khám không?
- **NV:** Anh/chị nên khám Cơ xương khớp để bác sĩ đánh giá trước; việc cần chụp loại nào, nếu có, phụ thuộc thăm khám và chỉ định chuyên môn.
- **KH:** Tôi thấy MRI tận mấy triệu nên hơi lo.
- **NV:** Dạ, giá khám Cơ xương khớp là 1.800.000 đồng/lần, còn MRI là dịch vụ riêng giá 4.500.000 đồng/lần nếu được chỉ định và đủ điều kiện thực hiện. Không phải trường hợp đau khớp nào cũng cần MRI.
- **KH:** Nếu không chụp thì bác sĩ có khám được không?
- **NV:** Bước thăm khám lâm sàng giúp bác sĩ đánh giá và quyết định có cần cận lâm sàng bổ sung hay không. Em không thể hứa trước phương án cụ thể.
- **KH:** Được, tôi đặt khám trước.

## CLI-048 — Dấu hiệu đột quỵ đột ngột

- Split: **test** · Link: `LEAD` `01eb3d5d-c636-ba5f-aa81-4504af0f83eb` · Offering: `CLI-SP-NEURO` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Ba tôi tự nhiên nói đớ, một bên tay yếu, tôi đặt khám thần kinh ngay hôm nay được không?
- **NV:** Anh/chị gọi 115 ngay. Nói khó và yếu một bên tay xuất hiện đột ngột có thể là dấu hiệu đột quỵ, cần cấp cứu khẩn cấp, không chờ đặt khám thần kinh thông thường.
- **KH:** Nhưng ba vẫn tỉnh, tôi chở xe máy qua được không?
- **NV:** Anh/chị nên gọi cấp cứu để được hướng dẫn vận chuyển phù hợp, tránh tự trì hoãn hoặc tự xử trí qua tin nhắn. Nếu có thể, ghi nhớ thời điểm cuối cùng ba còn bình thường để báo nhân viên y tế.
- **KH:** Tôi gọi 115 đây.
- **NV:** Vâng, anh/chị ưu tiên làm theo hướng dẫn cấp cứu ngay ạ.

## CLI-049 — Người nhà lo về khu cấp cứu cách ly

- Split: **test** · Link: `LEAD` `01fd833d-c06c-2653-a5bd-c8c9c498a972` · Offering: `CLI-EMG-ISOLATION` · Giá W12: **1.100.000 đồng/lần**

- **KH:** Người nhà tôi đang khó thở, tôi thấy bên mình có khám cấp cứu cách ly. Phòng có nhận ngay không?
- **NV:** Nếu người nhà đang khó thở nặng, anh/chị cần gọi 115 hoặc đưa đến cơ sở cấp cứu phù hợp ngay. Em không có thông tin trực cấp cứu và khả năng tiếp nhận cách ly tại thời điểm này để xác nhận qua Zalo.
- **KH:** Tôi thấy giá 1 triệu 1, sợ tới lại phát sinh.
- **NV:** Giá niêm yết của mã khám cấp cứu cách ly là 1.100.000 đồng/lần, không đại diện cho toàn bộ chi phí xử trí và cũng không bảo đảm có chỗ tiếp nhận. Việc cấp cứu cần ưu tiên trước câu hỏi về phí.
- **KH:** Vậy tôi gọi 115 cho chắc.
- **NV:** Dạ, anh/chị liên hệ cấp cứu ngay nhé.

## CLI-050 — Khách đòi cam kết tìm ra bệnh

- Split: **test** · Link: `LEAD` `0208c4d5-aabe-f050-aa26-495a7dec9a03` · Offering: `CLI-SP-NEURO` · Giá W12: **1.800.000 đồng/lần**

- **KH:** Khám thần kinh 1tr8 có chắc tìm ra bệnh không? Tôi đi nhiều nơi rồi mà vẫn chưa biết nguyên nhân.
- **NV:** Em hiểu anh/chị đã mất nhiều thời gian và chi phí nên muốn có câu trả lời rõ ràng. Tuy nhiên không cơ sở nào nên cam kết xác định được nguyên nhân chỉ sau một lần khám.
- **KH:** Vậy bỏ tiền ra rồi vẫn có thể chưa biết à?
- **NV:** Bác sĩ sẽ đánh giá dựa trên triệu chứng, thăm khám và thông tin chuyên môn; có trường hợp cần theo dõi hoặc kiểm tra thêm. Anh/chị có quyền hỏi rõ mục tiêu khám và những bước tiếp theo.
- **KH:** Thế phí chính xác bao nhiêu?
- **NV:** Khám Nội thần kinh đang niêm yết 1.800.000 đồng/lần. Chi phí kỹ thuật bổ sung, nếu có, cần được giải thích riêng trước khi thực hiện.
- **KH:** Tôi suy nghĩ thêm rồi liên hệ.

---

## Checklist nghiệm thu trước khi tích hợp

- Đối chiếu 50 Customer/Lead UUID và 50 offering FK với W12 canonical, tenant `cli`, trạng thái ACTIVE.
- Kiểm tra từng giá/đơn vị tính; xác minh chính sách bao gồm/không bao gồm xét nghiệm trước khi công bố như chính sách thật.
- Chạy kiểm tra an toàn nội dung y tế, ưu tiên tình huống cấp cứu; không để nhân viên CSKH chẩn đoán, chỉ định hoặc hứa lịch.
- Gán intent, emotion, urgency dựa trên từng inbound thực tế, không mặc định ASK_INFO/NEUTRAL.
- Codex chỉ tích hợp nguyên văn sau khi người dùng duyệt; kiểm tra exact-text, split, deterministic, SHA-256 và validator.
