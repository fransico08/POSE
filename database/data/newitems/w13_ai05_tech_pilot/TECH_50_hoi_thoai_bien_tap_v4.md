# W13 AI-05 — POSE Tech | 50 hội thoại biên tập TECH-001–TECH-050 — Bản 4 (rà soát ngữ cảnh và biên tập lại)

**Trạng thái:** Bản 4 rà soát lại 50 hội thoại theo tình huống, chờ người dùng duyệt; **chưa khóa nội dung, chưa tạo CSV/generator, chưa chạy validator hoặc nhập database**.

**Nguồn:** Báo cáo audit TECH W12 do Codex cung cấp, lấy từ `database/data/seed/multisector_v2`; chưa đối chiếu độc lập PostgreSQL. Tenant `tech` / POSE Tech / `001ba809-bda6-52d8-8645-5e4ba33533b4`. Chỉ dùng offering ACTIVE. Mapping 35 Customer + 15 Lead và split 40 dev / 10 test lấy nguyên từ báo cáo.

**Nguyên tắc:** KH = khách hàng/đầu mối doanh nghiệp, NV = nhân viên tư vấn/CSKH người thật. Nội dung tình huống là **giả lập biên tập**, không có nghĩa sự cố, hợp đồng, thanh toán, lịch triển khai hay quyền lợi đó đã tồn tại trong CRM W12. Những tính năng, chính sách, điều khoản, SLA, kết quả kỹ thuật chưa xác minh thì NV không được tự cam kết. Giá và đơn vị trong metadata phải giữ nguyên W12; một hội thoại có thể nhắc thêm offering ACTIVE khác để so sánh. Không xin mật khẩu, OTP, token hoặc dữ liệu khách hàng thô qua chat.

**Quy tắc biên tập bổ sung:** Khách hỏi gì, nhân viên xử lý đúng trọng tâm đó; không tự chào bán hoặc nhắc giá khi khách đang báo lỗi/khiếu nại. Với khách tìm hiểu gói, hỏi nhu cầu trước, giới thiệu lựa chọn phù hợp, chỉ báo giá khi khách hỏi hoặc đã chọn gói; không bịa tính năng chưa có trong W12. Xưng hô anh/em, chị/em, mình/bạn theo từng khách, nhất quán trong một cuộc. Không nhắc “W12”, “canonical”, “metadata” trong lời nói của nhân viên.

**Ghi chú chất lượng:** Độ dài và nhịp trò chuyện cố ý không đồng đều; có tình huống kết thúc mở. Khi duyệt, ưu tiên xem tính tự nhiên, ngữ cảnh, lời hứa chưa có chứng cứ và các offering liên quan. Mã TECH-xxx là mã biên tập, không phải conversation UUID.

---


**Lưu ý V3:** Nhân viên trả lời theo đúng câu hỏi khách vừa nêu, chủ động đề xuất bước tiếp theo; không dùng những câu đồng cảm rập khuôn. Giá chỉ xuất hiện trong lời thoại khi khách hỏi giá, cần so sánh gói, hoặc cần làm rõ chi phí; metadata vẫn giữ giá W12. Không tự khẳng định tính năng, phí phát sinh, SLA hay lịch triển khai khi chưa được xác minh.


**Lưu ý:** Giá trong metadata là thông tin liên kết W12, không phải điều khách đã biết. Không tự cho khách nói giá nếu trước đó chưa được báo giá. Những tình huống đang báo sự cố tuyệt đối không chuyển sang bán hàng.

## TECH-001 — Hotline không bắt máy khi cần gấp
- Split: **dev** · Link: `CUSTOMER` `191727d4-9f46-51dc-8eac-950824edb58b` · Offering: `TEC-SUP-STANDARD` · Giá W12: **3.000.000 đồng/tháng**
- **KH:** Em ơi, sáng giờ anh gọi hotline mấy lần mà không ai nghe. Cả phòng kinh doanh không đăng nhập được, khách đang chờ báo giá đây.
- **NV:** Dạ em xin lỗi anh, lúc cả phòng không vào được mà hotline lại không liên lạc được thì rất phiền. Anh cho em hỏi tất cả tài khoản đều bị hay chỉ một vài người ạ?
- **KH:** Cả phòng, từ khoảng 9 giờ. Giờ bên anh không tra được thông tin khách luôn.
- **NV:** Anh gửi em ảnh thông báo lỗi nhé, che thông tin khách hàng giúp em. Em cần biết thêm mọi người đang dùng cùng một mạng hay ở nhiều nơi để báo kỹ thuật kiểm tra đúng hướng.
- **KH:** Nó báo không thể kết nối. Có cần đưa mật khẩu để kỹ thuật vào xem không?
- **NV:** Không cần đâu anh, mình tuyệt đối không gửi mật khẩu hay OTP. Em chuyển ngay thông tin lỗi từ 9 giờ, ảnh màn hình và việc cả phòng bị ảnh hưởng cho kỹ thuật kiểm tra.
- **KH:** Anh gửi ảnh rồi đó. Nhờ bên em ưu tiên giùm, chứ cả phòng ngồi chờ thế này không ổn.
- **NV:** Dạ, em sẽ ghi rõ mức độ khẩn cấp và đề nghị bộ phận phụ trách phản hồi tình trạng xử lý cho anh. Em chưa có kết quả kiểm tra nên không hẹn bừa giờ khắc phục nhé.

## TECH-002 — Sàn thương mại điện tử cần đồng bộ đơn
- Split: **dev** · Link: `CUSTOMER` `2142de0a-6d8d-5552-8047-e25639e0181c` · Offering: `TEC-INT-ECOM` · Giá W12: **8.000.000 đồng/lần**
- **KH:** Em ơi, shop anh đang bán trên hai sàn, đơn về anh phải nhập tay vào kho cực quá. Bên em có làm kết nối được không?
- **NV:** Dạ bên em có dịch vụ tích hợp sàn thương mại điện tử anh nhé. Anh đang bán trên hai sàn nào, với kho mình dùng phần mềm gì ạ?
- **KH:** Shopee với TikTok Shop. Kho anh dùng phần mềm riêng, muốn đơn và tồn kho tự cập nhật qua lại.
- **NV:** Dạ, em ghi nhận hai sàn và phần mềm kho riêng. Để biết kết nối được đến đâu, bên kỹ thuật cần xem phần mềm kho có hỗ trợ kết nối không và dữ liệu mình muốn đồng bộ theo chiều nào.
- **KH:** Anh sợ nhất là làm giữa chừng lại báo thêm tiền.
- **NV:** Anh yên tâm, em sẽ nhờ kỹ thuật rà soát cả hai sàn và phần mềm kho, rồi gửi anh phạm vi công việc cùng báo giá rõ từng phần. Có hạng mục nào ngoài phạm vi ban đầu, bên em sẽ trao đổi để anh cân nhắc trước, không tự tính thêm vào báo giá.
- **KH:** Vậy bên em cần anh chuẩn bị gì?
- **NV:** Anh gửi em tên phần mềm kho, đầu mối kỹ thuật bên đó và ví dụ một đơn hàng đã che thông tin khách nhé. Em tổng hợp yêu cầu để bên phụ trách kiểm tra, rồi mới tư vấn phương án cụ thể cho anh.

## TECH-003 — Đơn trùng sau khi kết nối
- Split: **dev** · Link: `CUSTOMER` `2a290464-a2b5-5715-80bb-05f9c7c1119d` · Offering: `TEC-INT-ECOM` · Giá W12: **8.000.000 đồng/lần**
- **KH:** Em ơi, đơn trên sàn đổ về bị trùng. Kho chị suýt đóng hai kiện cho cùng một khách rồi.
- **NV:** Dạ chị, mình tạm nhờ kho đối chiếu mã đơn gốc trước khi đóng hàng giúp em nhé. Chị thấy đơn trùng từ lúc nào ạ?
- **KH:** Sáng nay. Cùng một mã đơn mà hiện hai dòng trong hệ thống.
- **NV:** Chị gửi em ảnh hai dòng đó, che tên và số điện thoại khách giúp em. Em sẽ báo kỹ thuật kiểm tra luồng đồng bộ và lý do tạo bản ghi trùng.
- **KH:** Có cần tắt kết nối sàn không? Chị sợ trùng thêm.
- **NV:** Mình chưa nên tự tắt khi chưa biết ảnh hưởng đến đơn đang xử lý chị nhé. Trước mắt kho kiểm tra mã đơn gốc để tránh đóng trùng, còn em chuyển trường hợp này sang kỹ thuật xem cách xử lý an toàn.
- **KH:** Ừ, chị gửi ảnh ngay. Có tin gì báo chị sớm nhé.

## TECH-004 — Chủ cửa hàng hỏi CRM Basic
- Split: **dev** · Link: `CUSTOMER` `3593fc80-7c73-5ad9-8204-68c7f677d5f0` · Offering: `CRM_BASIC` · Giá W12: **1.900.000 đồng/gói/tháng**
- **KH:** Chào em, chị có cửa hàng nhỏ, đang tìm phần mềm để quản lý khách quen. Bên em có gói nào không?
- **NV:** Dạ em chào chị. Bên em có POSE CRM Basic và CRM Pro. Chị đang muốn lưu thông tin khách để chăm sóc lại hay cần thêm báo cáo bán hàng ạ?
- **KH:** Chị muốn lưu số điện thoại, biết khách mua gì lần trước. Đừng phức tạp quá.
- **NV:** Dạ, em ghi đúng hai nhu cầu đó. Em sẽ kiểm tra tính năng cụ thể của Basic trước khi tư vấn chị chọn, vì tên gói chưa đủ để khẳng định có sẵn lịch sử mua hàng.
- **KH:** Basic bao nhiêu một tháng em?
- **NV:** Dạ giá niêm yết POSE CRM Basic là 1.900.000 đồng/tháng chị nhé. Em sẽ gửi chị phần tính năng được xác nhận, nhất là lưu khách và lịch sử mua, để chị xem có đáng dùng cho cửa hàng mình không.
- **KH:** Ừ, chị xem được những gì rồi mới quyết.

## TECH-005 — So sánh CRM Pro với Basic
- Split: **dev** · Link: `CUSTOMER` `3728c9ef-f35b-5077-9bbb-a9e783a60237` · Offering: `CRM_PRO` · Giá W12: **4.900.000 đồng/gói/tháng**
- **KH:** Anh đang phân vân CRM Basic với Pro, em tư vấn giúp anh được không?
- **NV:** Dạ được anh. Bên mình có khoảng bao nhiêu nhân viên dùng, và điều gì khiến anh muốn xem bản Pro ạ?
- **KH:** Tầm 12 người. Anh cần phân quyền và xem hiệu quả từng nhân viên.
- **NV:** Dạ, vậy hai mục quan trọng nhất là phân quyền và báo cáo theo nhân viên. Em sẽ nhờ đội sản phẩm đối chiếu chính xác hai mục này ở từng gói, tránh tư vấn theo tên Basic hay Pro thôi.
- **KH:** Thế giá hai gói chênh nhau nhiều không?
- **NV:** CRM Basic niêm yết 1.900.000 đồng/tháng, CRM Pro là 4.900.000 đồng/tháng anh nhé, chênh 3 triệu mỗi tháng. Nhưng mình nên xem bảng quyền lợi trước rồi mới quyết định có cần Pro không.
- **KH:** Ừ, gửi anh so sánh đúng hai tính năng đó trước.

## TECH-006 — Sếp cần dashboard nhưng chưa rõ chỉ số
- Split: **dev** · Link: `CUSTOMER` `414797a2-f48f-59e3-9265-3caded5696a5` · Offering: `AN_ENT` · Giá W12: **8.900.000 đồng/gói/tháng**
- **KH:** Em ơi, sếp chị muốn có một màn hình xem doanh thu từng chi nhánh. Bên em có giải pháp không?
- **NV:** Dạ có dịch vụ Analytics và thiết kế báo cáo chị nhé. Hiện số liệu các chi nhánh đang nằm ở đâu ạ?
- **KH:** Mỗi chi nhánh gửi một file Excel, cuối ngày chị phải ghép lại.
- **NV:** Vậy trước tiên mình cần xem ba file có cùng cách ghi doanh thu không. Chị gửi em mẫu đã bỏ thông tin nhạy cảm và một ảnh báo cáo sếp muốn xem nhé.
- **KH:** Nếu làm được thì chị muốn khỏi phải ghép thủ công nữa.
- **NV:** Em ghi nhận mong muốn tự tổng hợp. Bên kỹ thuật sẽ kiểm tra nguồn dữ liệu có hỗ trợ cách đó không rồi em mới tư vấn chị dùng Analytics hay cần thiết kế riêng.
- **KH:** Được, để chị chuẩn bị file mẫu.

## TECH-007 — Khách muốn cam kết số liệu luôn đúng
- Split: **dev** · Link: `CUSTOMER` `415fbab7-cc39-5365-83dc-3e45286adc18` · Offering: `AN_ENT` · Giá W12: **8.900.000 đồng/gói/tháng**
- **KH:** Analytics Enterprise mà dùng thì số liệu có chắc chắn không lệch không em?
- **NV:** Dạ em không dám hứa tuyệt đối khi chưa xem dữ liệu đầu vào anh ạ. Bên anh đang bị lệch chỉ tiêu nào?
- **KH:** Doanh thu. Tháng trước kế toán với kinh doanh ra hai số khác nhau, sếp hỏi rất căng.
- **NV:** Anh cho em biết hai bên đang tính doanh thu theo đơn tạo, đơn hoàn tất hay tiền đã thu nhé. Chỉ cần khác cách tính là số đã có thể không giống nhau.
- **KH:** Nếu mua Enterprise thì bên em xử lý được chuyện đó chứ?
- **NV:** Em sẽ nhờ đội giải pháp xem nguồn dữ liệu và cách đối soát trước, rồi mới xác nhận giải pháp có đáp ứng không. Em không muốn anh mua gói rồi vẫn vướng đúng vấn đề cũ.
- **KH:** Ừ, anh gửi hai mẫu báo cáo đã ẩn dữ liệu.

## TECH-008 — Zalo OA có gửi tự động không
- Split: **dev** · Link: `CUSTOMER` `454442aa-d867-5038-88ae-d7f76d4f5e30` · Offering: `TEC-INT-ZALO` · Giá W12: **7.000.000 đồng/lần**
- **KH:** Bên bạn có kết nối Zalo OA để gửi thông báo đơn hàng không?
- **NV:** Dạ bên em có dịch vụ tích hợp Zalo OA/ZNS. Bạn đã có OA rồi hay đang chuẩn bị đăng ký?
- **KH:** Có rồi. Mình muốn khách đặt đơn xong nhận thông báo, vậy có gửi tự động được không?
- **NV:** Mình cần kiểm tra loại thông báo, hệ thống đơn hàng và điều kiện gửi của Zalo trước khi xác nhận được bạn nhé. Bạn đang lưu trạng thái đơn ở phần mềm nào?
- **KH:** Phần mềm bán hàng riêng. Dịch vụ tích hợp giá sao?
- **NV:** Giá niêm yết hạng mục tích hợp Zalo OA/ZNS là 7.000.000 đồng/lần. Phí này chưa đủ để kết luận các khoản vận hành hay gửi tin đều đã bao gồm; em sẽ nhờ bộ phận phụ trách làm rõ từng khoản.
- **KH:** Ok, mình cần bảng chi phí rõ ràng.

## TECH-009 — Khách bán hàng online hỏi tích hợp nhiều sàn
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002871` · Offering: `TEC-INT-ECOM` · Giá W12: **8.000.000 đồng/lần**
- **KH:** Em ơi, chị bán ba sàn mà đang quản lý kho bằng Excel. Có cách nào gom đơn lại không?
- **NV:** Dạ có thể xem phương án tích hợp sàn chị nhé. Nhưng nếu kho mình đang dùng Excel thì em cần hỏi thêm: chị muốn gom đơn để in phiếu, kiểm tồn hay theo dõi giao hàng là chính?
- **KH:** Cả ba, mà chị chưa có phần mềm kho. Bên em bán luôn phần mềm đó hả?
- **NV:** Dạ bên em có dịch vụ tích hợp sàn, còn phần mềm kho cụ thể có sẵn hay phải dùng giải pháp khác thì em cần kiểm tra. Em không muốn chị mua phần kết nối rồi mới biết thiếu hệ thống nhận đơn.
- **KH:** Vậy bắt đầu từ đâu cho đỡ tốn?
- **NV:** Chị mô tả quy trình từ lúc có đơn đến lúc đóng hàng giúp em. Em nhờ đội giải pháp xem mình cần hệ thống quản lý nào trước, rồi mới tính đến kết nối từng sàn.
- **KH:** Ừ, chị ghi lại quy trình cho em.

## TECH-010 — Khách cần hỗ trợ theo giờ ngay
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002873` · Offering: `TEC-SUP-ADHOC` · Giá W12: **500.000 đồng/giờ**
- **KH:** Em ơi, máy của ba bạn trong phòng anh cứ báo lỗi. Bên em có người hỗ trợ ngay không?
- **NV:** Dạ bên em có dịch vụ hỗ trợ kỹ thuật theo yêu cầu. Ba máy đang báo cùng một lỗi hay mỗi máy một kiểu anh?
- **KH:** Cùng báo không vào được. Anh không rành kỹ thuật, muốn có người xem giúp.
- **NV:** Dạ anh chụp giúp em thông báo lỗi, che thông tin nhạy cảm. Em chuyển kỹ thuật đánh giá cách hỗ trợ phù hợp; hiện em chưa xác nhận được có thể kết nối từ xa ngay hay không.
- **KH:** Nếu nhờ bên em thì tính tiền thế nào?
- **NV:** Hỗ trợ theo yêu cầu niêm yết 500.000 đồng/giờ. Em sẽ hỏi rõ cách tính giờ và thời điểm bắt đầu trước khi anh quyết định dùng dịch vụ.
- **KH:** Ừ, kiểm tra rồi báo anh, đừng tự tính giờ lúc đang chờ nhé.

## TECH-011 — Công ty 8 người chọn gói
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002874` · Offering: `TEC-SAS-BASIC` · Giá W12: **1.990.000 đồng/tháng**
- **KH:** Bên mình có tám người dùng phần mềm, gói Starter được không em?
- **NV:** Dạ Starter ghi quy mô 1–5 user nên chưa phù hợp nếu cả tám người đều cần tài khoản. Bên em có gói Basic 1–10 user để mình cân nhắc.
- **KH:** Có ba người chỉ vào xem, không nhập gì. Vậy tính là user không?
- **NV:** Phần tài khoản chỉ xem có tính vào số user hay không em cần xác nhận chính sách của gói. Em chưa muốn bảo anh mua gói lớn khi chưa rõ.
- **KH:** Giá Starter với Basic bao nhiêu?
- **NV:** Starter niêm yết 990.000 đồng/tháng, Basic 1–10 user là 1.990.000 đồng/tháng anh nhé. Em sẽ kiểm tra quy tắc tính tài khoản rồi báo anh phương án vừa đủ dùng.
- **KH:** Ok, bên anh ưu tiên tiết kiệm.

## TECH-012 — Triển khai tiêu chuẩn khác cơ bản ra sao
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002876` · Offering: `TEC-IMP-STANDARD` · Giá W12: **12.000.000 đồng/dự án**
- **KH:** Em ơi, triển khai cơ bản với tiêu chuẩn khác nhau sao? Anh đang chọn cho công ty.
- **NV:** Dạ hai dịch vụ có tên và mức giá khác nhau, còn phạm vi công việc cụ thể cần đội triển khai xác nhận. Công ty anh có mấy bộ phận sẽ dùng hệ thống ạ?
- **KH:** Ba phòng ban, hai chi nhánh. Anh muốn xong trước cuối tháng.
- **NV:** Em ghi ba phòng ban, hai chi nhánh và mốc mong muốn cuối tháng. Anh đang cần chuyển dữ liệu cũ hay chỉ thiết lập hệ thống mới?
- **KH:** Có dữ liệu khách cũ nữa. Báo anh giá để trình sếp trước đi.
- **NV:** Dạ triển khai cơ bản niêm yết 5 triệu/dự án, tiêu chuẩn 12 triệu/dự án. Đây là giá hạng mục, chưa phải báo giá chốt cho phạm vi hai chi nhánh có chuyển dữ liệu. Em sẽ nhờ đội dự án làm rõ phần việc và tiến độ khả thi để anh trình sếp.
- **KH:** Ừ, anh cần bản dự toán có ghi rõ phần nào chưa tính.

## TECH-013 — Khách đòi module tùy chỉnh
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002877` · Offering: `TEC-DEV-MODULE` · Giá W12: **15.000.000 đồng/module**
- **KH:** Bên em có nhận làm thêm màn hình duyệt chiết khấu không? Công ty anh đang duyệt bằng Excel.
- **NV:** Dạ có dịch vụ phát triển module tùy chỉnh anh nhé. Hiện bên anh duyệt một cấp hay nhiều cấp ạ?
- **KH:** Hai cấp, trưởng nhóm duyệt rồi tới giám đốc. Có vài trường hợp ngoại lệ nữa.
- **NV:** Anh gửi em mẫu quy trình hoặc file Excel đã bỏ dữ liệu nội bộ nhé. Em nhờ đội kỹ thuật đánh giá màn hình, quyền duyệt và các trường hợp ngoại lệ.
- **KH:** Anh thấy ghi 15 triệu/module, vậy là làm được hết đúng không?
- **NV:** Dạ 15 triệu đồng/module là giá niêm yết, còn phạm vi hai cấp duyệt và ngoại lệ phải được xác nhận trước. Em sẽ làm rõ những phần nằm trong báo giá để anh không bị bất ngờ sau này.
- **KH:** Ok, anh gửi sơ đồ.

## TECH-014 — Tích hợp sàn bị chậm, khách bực
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002879` · Offering: `TEC-INT-ECOM` · Giá W12: **8.000.000 đồng/lần**
- **KH:** Chị hỏi tiến độ kết nối sàn ba lần rồi, lần nào cũng bảo chờ. Giờ kho không biết sắp xếp ca làm sao hết.
- **NV:** Dạ em xin lỗi chị vì bên em chưa phản hồi rõ. Chị cho em xin mã yêu cầu hoặc nội dung trao đổi gần nhất, em kiểm tra đúng đầu việc đang vướng.
- **KH:** Chị có email đây. Nhưng chị cần biết cụ thể chậm ở đâu, đừng nói đang xử lý nữa.
- **NV:** Dạ, em sẽ chuyển yêu cầu xác nhận ba thông tin: hạng mục nào chưa xong, đang chờ bên nào và bước tiếp theo là gì. Em chưa có kết quả nên không nói đại với chị.
- **KH:** Chị còn phải báo lại quản lý kho chiều nay.
- **NV:** Em ghi rõ chị cần thông tin để sắp ca kho chiều nay và đề nghị người phụ trách phản hồi sớm. Nếu chưa có mốc hoàn thành, em sẽ yêu cầu họ nói rõ phần nào còn chưa xác định.
- **KH:** Ừ, chị chờ thông tin cụ thể.

## TECH-015 — Mười người nhưng muốn thêm tài khoản
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002881` · Offering: `TEC-SAS-BASIC` · Giá W12: **1.990.000 đồng/tháng**
- **KH:** Gói Basic 1–10 user bên anh đang dùng, giờ thêm người thứ mười một thì sao em?
- **NV:** Dạ nếu cần 11 tài khoản thì vượt quy mô ghi trong tên gói Basic. Bên em có Professional 1–30 user để mình xem xét.
- **KH:** Nhưng người mới chỉ làm thời vụ hai tháng thôi.
- **NV:** Vậy anh muốn tránh nâng gói lâu dài vì nhu cầu tạm thời đúng không ạ? Em sẽ kiểm tra điều kiện thay đổi số tài khoản và gói, chứ chưa khuyên anh nâng ngay.
- **KH:** Nếu lên Professional thì bao nhiêu tiền?
- **NV:** Professional niêm yết 4.500.000 đồng/tháng anh nhé. Còn cách tính khi đổi gói giữa kỳ và có thể quay lại Basic sau đó hay không, em cần xác nhận trước.
- **KH:** Ừ, hỏi giúp anh cách tính trong hai tháng đó.

## TECH-016 — Zalo OA và chi phí phát sinh
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002882` · Offering: `TEC-INT-ZALO` · Giá W12: **7.000.000 đồng/lần**
- **KH:** Bên em kết nối Zalo OA được không? Chị muốn gửi thông báo giao hàng cho khách.
- **NV:** Dạ có dịch vụ tích hợp Zalo OA/ZNS chị nhé. Chị đang dùng phần mềm nào để theo dõi trạng thái giao hàng?
- **KH:** Bên chị có phần mềm riêng. Chị muốn gửi khi đơn đang giao với khi giao xong.
- **NV:** Dạ em ghi hai thời điểm đó. Em cần nhờ đội kỹ thuật kiểm tra cách lấy trạng thái đơn và điều kiện gửi thông báo của Zalo trước khi xác nhận phương án.
- **KH:** Chị hỏi trước là có phát sinh phí gửi tin không?
- **NV:** Phần phí gửi tin cần tách riêng theo điều kiện dịch vụ của nền tảng chị nhé. Em sẽ nhờ bên phụ trách làm bảng gồm phí tích hợp, phạm vi kết nối và khoản vận hành có thể phát sinh để chị duyệt trước.
- **KH:** Ừ, rõ từng khoản là được.

## TECH-017 — Dashboard khó đọc cho sếp
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002883` · Offering: `AN_ENT` · Giá W12: **8.900.000 đồng/gói/tháng**
- **KH:** Dashboard bên chị nhìn rối quá, sếp mở ra là hỏi sao nhiều số vậy.
- **NV:** Dạ chị cho em xem ảnh màn hình đã che số liệu nhạy cảm được không? Sếp muốn nhìn nhanh chỉ số nào nhất ạ?
- **KH:** Doanh thu và tỷ lệ hoàn thành mục tiêu. Mấy biểu đồ khác sếp không dùng.
- **NV:** Vậy mình nên ưu tiên hai chỉ số đó trên trang đầu. Em ghi yêu cầu bố cục lại và chuyển đội phụ trách kiểm tra có chỉnh được trong phạm vi hiện tại không.
- **KH:** Có phải mua thêm gói khác mới sửa được không?
- **NV:** Em chưa thể kết luận chị phải mua thêm. Để em kiểm tra trước phần cấu hình hiện có và phạm vi công việc; nếu có chi phí bổ sung, em sẽ báo rõ rồi chị mới quyết.
- **KH:** Được, chị gửi ảnh.

## TECH-018 — Module đã đề xuất nhưng khách đổi ý
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002884` · Offering: `TEC-DEV-MODULE` · Giá W12: **15.000.000 đồng/module**
- **KH:** Em ơi, cái module duyệt đơn hôm trước anh trao đổi, giờ sếp lại muốn thêm bước kiểm tra công nợ.
- **NV:** Dạ anh, em ghi thêm bước kiểm tra công nợ. Bước đó xảy ra trước hay sau khi trưởng bộ phận duyệt ạ?
- **KH:** Trước khi duyệt. Mà anh sợ đổi yêu cầu là bên em làm lại từ đầu.
- **NV:** Em sẽ cập nhật sơ đồ quy trình để đội kỹ thuật đánh giá phần nào giữ nguyên được, phần nào phải điều chỉnh. Hiện em chưa có cơ sở nói có cần làm lại hay không.
- **KH:** Có tăng phí thì báo trước cho anh nhé.
- **NV:** Dạ, em sẽ yêu cầu đối chiếu phạm vi đã trao đổi với yêu cầu mới, rồi gửi anh phần thay đổi và chi phí nếu có để anh xác nhận trước khi triển khai.
- **KH:** Ừ, anh gửi quy trình mới.

## TECH-019 — Business 100 user và phân quyền
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002885` · Offering: `TEC-SAS-BUSINESS` · Giá W12: **9.900.000 đồng/tháng**
- **KH:** Công ty chị khoảng 70 người, đang xem gói Business. Có phù hợp không em?
- **NV:** Dạ Business ghi quy mô 1–100 user, nên về số lượng người dùng thì nằm trong khoảng chị cần. Chị muốn mọi người dùng chung hay chia theo phòng ban ạ?
- **KH:** Chia phòng ban. Chị cần người phòng này không xem được dữ liệu phòng kia.
- **NV:** Dạ, phần phân quyền chi tiết theo phòng ban em cần kiểm tra tính năng thực tế của gói, chưa thể khẳng định chỉ dựa vào số user.
- **KH:** Giá gói đó bao nhiêu?
- **NV:** Business niêm yết 9.900.000 đồng/tháng chị nhé. Em sẽ nhờ đội sản phẩm xác nhận cách phân quyền trước, vì đó mới là điều quyết định gói có hợp với bên chị không.
- **KH:** Ừ, chị cần xem phân quyền rồi mới trình sếp.

## TECH-020 — App mobile cần báo giá
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002888` · Offering: `TEC-DEV-MOBILE` · Giá W12: **85.000.000 đồng/dự án**
- **KH:** Bên em có làm app cho khách đặt lịch không? Anh muốn có cả iPhone với Android.
- **NV:** Dạ bên em có dịch vụ phát triển app iOS/Android. Anh muốn khách đặt lịch cho dịch vụ gì và bên mình đã có hệ thống quản lý lịch chưa ạ?
- **KH:** Spa. Hiện nhân viên ghi lịch bằng sổ, anh muốn khách tự chọn giờ.
- **NV:** Dạ, em ghi nhu cầu đặt lịch và quản lý lịch từ sổ. Đội kỹ thuật cần xem quy trình nhận lịch, xác nhận lịch và dữ liệu khách trước khi đề xuất cách làm.
- **KH:** Chi phí làm app khoảng bao nhiêu em?
- **NV:** Dịch vụ phát triển app mobile đang niêm yết 85.000.000 đồng/dự án anh nhé. Phạm vi cụ thể và báo giá cuối cần chốt theo yêu cầu, em không muốn nói mức đó bao trọn mọi chức năng.
- **KH:** Vậy anh gửi quy trình đặt lịch hiện tại.

## TECH-021 — Khách muốn Zalo gửi theo trạng thái
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002889` · Offering: `TEC-INT-ZALO` · Giá W12: **7.000.000 đồng/lần**
- **KH:** Anh muốn khách được nhắn Zalo khi đơn chuyển sang đang giao, bên em làm được không?
- **NV:** Dạ bên em có dịch vụ tích hợp Zalo OA/ZNS. Hiện trạng thái đơn được cập nhật ở hệ thống nào anh?
- **KH:** Phần mềm bên anh tự viết. Khi shipper đổi trạng thái thì anh muốn Zalo gửi theo.
- **NV:** Vậy cần xem phần mềm của anh có xuất được sự kiện đổi trạng thái không, rồi mới đánh giá cách kết nối với Zalo. Anh có người phụ trách kỹ thuật hệ thống đó không?
- **KH:** Có, nhưng anh muốn biết có gửi được cho mọi khách không.
- **NV:** Điều kiện gửi còn phụ thuộc loại thông báo và quy định của Zalo anh nhé. Em sẽ nhờ bộ phận phụ trách kiểm tra trường hợp cụ thể, không hứa gửi được cho mọi số điện thoại.
- **KH:** Ok, anh nối em với bạn IT.

## TECH-022 — CRM Pro bị nhân viên phản đối
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002890` · Offering: `CRM_PRO` · Giá W12: **4.900.000 đồng/gói/tháng**
- **KH:** Bên chị định dùng CRM Pro mà nhân viên phản đối, bảo thao tác nhiều quá.
- **NV:** Dạ chị, các bạn đang vướng ở bước nào nhất: nhập khách, cập nhật cơ hội hay tìm thông tin ạ?
- **KH:** Nhập khách mới. Mấy bạn bảo phải điền nhiều ô, mất thời gian.
- **NV:** Chị gửi em ảnh màn hình nhập liệu đã che thông tin khách và mô tả những ô các bạn đang phải điền nhé. Em nhờ đội sản phẩm xem phần nào có thể cấu hình lại hoặc cần hướng dẫn thêm.
- **KH:** Chị không muốn mua gói khác chỉ để dễ dùng hơn đâu.
- **NV:** Dạ, em chưa đề xuất đổi gói chị nhé. Mình kiểm tra đúng thao tác đang gây khó trước, rồi mới tính phương án phù hợp.
- **KH:** Ừ, chị gửi ảnh cho em.

## TECH-023 — Tích hợp sàn và mã hàng không khớp
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002891` · Offering: `TEC-INT-ECOM` · Giá W12: **8.000.000 đồng/lần**
- **KH:** Em ơi, kết nối sàn xong mà mã hàng bên chị cứ không khớp. Kho đang phải sửa tay.
- **NV:** Dạ chị, mã không khớp là do mã trên sàn khác mã trong kho hay có sản phẩm bị gán nhầm ạ?
- **KH:** Cả hai. Có mẫu một màu mà trên sàn ghi một kiểu, kho ghi kiểu khác.
- **NV:** Vậy mình cần đối chiếu bảng mã hàng trước chị nhé. Chị gửi vài ví dụ đã che thông tin đơn hàng, em chuyển kỹ thuật kiểm tra quy tắc ghép mã.
- **KH:** Chị có nên sửa hết mã trong kho cho giống sàn không?
- **NV:** Chị khoan sửa hàng loạt khi chưa thống nhất quy tắc, tránh ảnh hưởng tồn kho. Em nhờ đội phụ trách kiểm tra và đề xuất cách đối chiếu trước khi thay đổi dữ liệu thật.
- **KH:** Ừ, chị gửi mẫu.

## TECH-024 — Nâng gói giữa tháng
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002892` · Offering: `TEC-SAS-BUSINESS` · Giá W12: **9.900.000 đồng/tháng**
- **KH:** Anh đang dùng Business, muốn đổi gói giữa tháng thì tính tiền sao em?
- **NV:** Dạ anh muốn chuyển lên gói nào hay đang tính giảm xuống ạ?
- **KH:** Anh muốn lên Enterprise vì sắp thêm nhân viên. Nhưng chưa chắc tăng ngay.
- **NV:** Dạ, em ghi nhu cầu dự kiến tăng người dùng. Enterprise ghi unlimited user, còn chính sách tính phí khi đổi giữa kỳ em cần kiểm tra trước khi báo anh.
- **KH:** Vậy tháng này có phải trả hai lần không?
- **NV:** Em chưa xác nhận được cách tính bù trừ hay thời điểm áp dụng. Em sẽ nhờ bộ phận phụ trách gửi cách tính cụ thể để anh xem rồi mới quyết định đổi.
- **KH:** Ừ, có bảng tính rồi anh mới chốt.

## TECH-025 — Khách hỏi app có tích hợp CRM
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002895` · Offering: `TEC-DEV-MOBILE` · Giá W12: **85.000.000 đồng/dự án**
- **KH:** Anh muốn làm app cho nhân viên đi thị trường, nhưng dữ liệu khách đang ở CRM cũ. Có nối được không?
- **NV:** Dạ bên em có dịch vụ phát triển app mobile. Anh đang dùng CRM nào, và nhân viên cần xem hay cập nhật những thông tin gì khi đi thị trường ạ?
- **KH:** Xem lịch sử khách, ghi đơn mới. Anh không muốn nhập lại dữ liệu.
- **NV:** Dạ, em ghi hai nhu cầu đó. Bên kỹ thuật cần xem hệ thống CRM cũ có cách kết nối hoặc xuất dữ liệu phù hợp không rồi mới xác nhận được phương án.
- **KH:** Nếu làm app mà không nối được CRM thì anh không làm đâu.
- **NV:** Dạ vậy mình kiểm tra khả năng kết nối trước khi đi sâu vào thiết kế app anh nhé. Anh cho em đầu mối kỹ thuật của hệ thống CRM hiện tại để hai bên trao đổi.
- **KH:** Được, anh gửi liên hệ IT.

## TECH-026 — Gói năm có tiết kiệm không
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002896` · Offering: `TEC-SAS-ANNUAL` · Giá W12: **43.200.000 đồng/năm**
- **KH:** Anh thấy có gói Professional theo năm. So với trả tháng thì thế nào em?
- **NV:** Dạ gói Professional 1–30 user theo tháng niêm yết 4.500.000 đồng/tháng, còn gói năm Professional là 43.200.000 đồng/năm anh nhé.
- **KH:** Nếu lấy 4,5 triệu nhân 12 thì 54 triệu. Vậy gói năm rẻ hơn 10,8 triệu đúng không?
- **NV:** Dạ đúng về phép tính giá niêm yết anh ạ: 54 triệu so với 43,2 triệu, chênh 10,8 triệu. Nhưng điều kiện hợp đồng và thanh toán năm em cần xác nhận thêm trước khi anh ký.
- **KH:** Anh muốn biết giữa năm thêm người thì sao.
- **NV:** Em sẽ hỏi rõ quy định số user và thay đổi gói trong thời hạn năm, rồi gửi anh thông tin để mình so sánh tổng chi phí cho đúng.
- **KH:** Ok, anh đợi điều kiện cụ thể.

## TECH-027 — Khách nghi phí hỗ trợ tính sai
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002897` · Offering: `CRM_PRO` · Giá W12: **4.900.000 đồng/gói/tháng**
- **KH:** Em ơi, hóa đơn bên anh có khoản hỗ trợ mà anh không nhớ đăng ký. Kiểm tra giúp anh.
- **NV:** Dạ anh gửi em mã hóa đơn hoặc mã yêu cầu để em đối chiếu nhé, không cần gửi thông tin thẻ hay tài khoản thanh toán.
- **KH:** Anh gửi mã rồi. Tháng trước không có khoản này.
- **NV:** Dạ em nhận được mã. Em sẽ chuyển bộ phận phụ trách kiểm tra khoản phí đó phát sinh từ dịch vụ nào và căn cứ tính phí, rồi phản hồi anh rõ từng mục.
- **KH:** Anh không muốn bị thu tiền cho thứ chưa đồng ý.
- **NV:** Dạ, em ghi nhận yêu cầu đối chiếu xác nhận dịch vụ trước khi tính phí. Hiện em chưa kiểm tra xong nên không khẳng định khoản này đúng hay sai; em sẽ đề nghị cung cấp chứng từ hoặc thông tin liên quan để anh kiểm tra.
- **KH:** Ừ, cần trả lời rõ giúp anh.

## TECH-028 — Kết nối API với hệ thống cũ
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002898` · Offering: `TEC-INT-API` · Giá W12: **6.000.000 đồng/lần**
- **KH:** Bên chị có phần mềm nội bộ khá cũ. Muốn nối dữ liệu qua hệ thống mới thì làm được không?
- **NV:** Dạ bên em có dịch vụ tích hợp API bên thứ ba. Phần mềm cũ của chị có người quản trị hoặc nhà cung cấp còn hỗ trợ không ạ?
- **KH:** Nhà cung cấp cũ nghỉ rồi, bên chị chỉ còn một bạn IT.
- **NV:** Vậy trước tiên cần xem hệ thống có tài liệu kết nối hoặc cách xuất dữ liệu nào không. Chị cho em đầu mối IT để kỹ thuật trao đổi nhé.
- **KH:** Chị sợ động vào rồi phần mềm cũ hỏng luôn.
- **NV:** Dạ, em sẽ ghi rõ yêu cầu không can thiệp trực tiếp vào hệ thống đang vận hành khi chưa đánh giá rủi ro và thống nhất phương án. Bên kỹ thuật cần kiểm tra trước rồi mới đề xuất bước tiếp theo.
- **KH:** Ừ, để chị trao đổi với IT.

## TECH-029 — Starter dành cho nhóm mới
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002901` · Offering: `TEC-SAS-STARTER` · Giá W12: **990.000 đồng/tháng**
- **KH:** Nhóm em mới có bốn người, muốn dùng phần mềm chung. Gói Starter hợp không chị?
- **NV:** Chào em, Starter ghi quy mô 1–5 user nên về số lượng thì phù hợp với nhóm bốn người. Nhóm mình cần quản lý công việc hay thông tin khách hàng là chính?
- **KH:** Bọn em muốn theo dõi khách đang tư vấn, ai phụ trách khách nào.
- **NV:** Chị ghi nhận hai nhu cầu đó. Phần tính năng phân công và theo dõi khách trong Starter cần đội sản phẩm xác nhận trước khi chị tư vấn em chốt gói.
- **KH:** Starter giá bao nhiêu chị?
- **NV:** Giá niêm yết là 990.000 đồng/tháng em nhé. Chị sẽ gửi em thông tin tính năng đã xác nhận để nhóm xem có đáp ứng được công việc hằng ngày không.
- **KH:** Dạ, chị gửi giúp em.

## TECH-030 — App mobile bị ép deadline
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002902` · Offering: `TEC-DEV-MOBILE` · Giá W12: **85.000.000 đồng/dự án**
- **KH:** Anh cần làm app trước hội chợ tháng sau, bên em có kịp không?
- **NV:** Dạ anh, để đánh giá tiến độ em cần biết app dùng cho việc gì và hiện bên anh đã có bản thiết kế hay chưa ạ?
- **KH:** Chưa có gì. Anh muốn khách quét mã xem sản phẩm rồi để lại thông tin.
- **NV:** Dạ, em ghi hai chức năng chính và mốc hội chợ. Em sẽ nhờ đội dự án xem phạm vi công việc, các bước cần làm và thời gian khả thi trước khi nhận lời.
- **KH:** Anh cần câu trả lời nhanh, không kịp là anh tìm bên khác.
- **NV:** Dạ, em sẽ chuyển yêu cầu cần đánh giá tiến độ gấp cho đội phụ trách. Em không muốn hứa kịp hội chợ khi chưa có kế hoạch kỹ thuật; bên em sẽ phản hồi rõ có nhận được mốc đó hay không sau khi kiểm tra.
- **KH:** Ừ, cứ nói thẳng giúp anh.

## TECH-031 — Gói năm và thay đổi số nhân sự
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002903` · Offering: `TEC-SAS-ANNUAL` · Giá W12: **43.200.000 đồng/năm**
- **KH:** Công ty chị định mua Professional theo năm, nhưng nhân sự lên xuống liên tục. Có bất tiện không em?
- **NV:** Dạ chị đang dự kiến dao động khoảng bao nhiêu tài khoản ạ?
- **KH:** Lúc 18 người, lúc gần 35. Chị sợ mua năm rồi vượt số lượng.
- **NV:** Gói Professional theo tháng ghi 1–30 user, còn điều kiện số user của gói năm và cách xử lý khi vượt giới hạn em cần xác nhận riêng. Em sẽ không tự suy từ tên gói.
- **KH:** Nếu vượt thì có được nâng gói giữa năm không?
- **NV:** Em sẽ hỏi chính sách đổi gói và phần phí điều chỉnh nếu có, rồi gửi chị để tính trước. Chị nên có thông tin đó trước khi quyết định cam kết theo năm.
- **KH:** Ừ, chị chờ chính sách rõ ràng.

## TECH-032 — Hỗ trợ theo giờ vì nhân viên thao tác sai
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002904` · Offering: `TEC-SUP-ADHOC` · Giá W12: **500.000 đồng/giờ**
- **KH:** Em ơi, nhân viên bên anh thao tác nhầm, giờ một loạt dữ liệu hiển thị sai. Có người hỗ trợ không?
- **NV:** Dạ anh, mình tạm ngừng thao tác chỉnh sửa hàng loạt giúp em nhé. Dữ liệu sai là tên khách, trạng thái đơn hay phần nào ạ?
- **KH:** Trạng thái khách. Bạn ấy bấm cập nhật hàng loạt.
- **NV:** Anh cho em biết khoảng thời gian thao tác và có bao nhiêu bản ghi bị ảnh hưởng. Mình đừng gửi file dữ liệu khách qua chat, chỉ cần ảnh minh họa đã che thông tin nhạy cảm.
- **KH:** Có khôi phục lại được không?
- **NV:** Em cần kỹ thuật kiểm tra lịch sử thay đổi và khả năng khôi phục của hệ thống trước, chưa dám khẳng định được anh nhé. Em sẽ ghi rõ đây là thay đổi hàng loạt để bộ phận phụ trách đánh giá.
- **KH:** Ừ, anh chờ, không cho ai sửa tiếp nữa.

## TECH-033 — API bị trả lỗi và không biết nguyên nhân
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002905` · Offering: `TEC-INT-API` · Giá W12: **6.000.000 đồng/lần**
- **KH:** Bên em ơi, API của anh đang trả lỗi liên tục, đơn không đẩy qua hệ thống nữa.
- **NV:** Dạ anh, lỗi bắt đầu từ lúc nào và mã phản hồi đang là gì ạ? Anh chỉ gửi mã lỗi, không gửi token hay khóa API nhé.
- **KH:** Từ trưa, trả 401. Trước đó vẫn chạy bình thường.
- **NV:** Dạ em ghi lỗi 401 từ trưa. Mã này thường liên quan xác thực, nhưng phải xem log đã ẩn dữ liệu nhạy cảm mới xác định nguyên nhân cụ thể được.
- **KH:** Anh cần bên em kiểm tra, giờ đơn đang ùn.
- **NV:** Em chuyển tình trạng đơn bị ùn và mã lỗi cho kỹ thuật kiểm tra theo mức độ ảnh hưởng. Anh gửi em thời điểm lỗi và một mã yêu cầu nếu đã có để bên em đối chiếu nhé.
- **KH:** Ok, anh gửi thời điểm với mã lỗi.

## TECH-034 — Tư vấn giải pháp theo giờ
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002906` · Offering: `TEC-DEV-CONSULT` · Giá W12: **800.000 đồng/giờ**
- **KH:** Chị có một dự án công nghệ nhưng chưa biết nên làm app hay web. Bên em có tư vấn không?
- **NV:** Dạ có dịch vụ tư vấn giải pháp công nghệ chị nhé. Chị muốn người dùng làm việc gì là chính ạ?
- **KH:** Nhân viên giao hàng cần cập nhật trạng thái, quản lý thì muốn xem báo cáo.
- **NV:** Vậy em sẽ ghi hai nhóm người dùng và quy trình giao hàng hiện tại. Buổi tư vấn có thể bắt đầu từ nhu cầu rồi mới đánh giá phương án app hay web, chưa cần chốt công nghệ ngay.
- **KH:** Dịch vụ tư vấn tính phí thế nào?
- **NV:** Giá niêm yết là 800.000 đồng/giờ chị nhé. Em sẽ nhờ bộ phận phụ trách xác nhận phạm vi và cách tính thời lượng trước khi chị đặt lịch.
- **KH:** Ừ, chị muốn biết cần chuẩn bị gì cho buổi tư vấn.

## TECH-035 — Tích hợp sàn nhưng khách muốn tự làm
- Split: **dev** · Link: `CUSTOMER` `50000000-0000-5000-8000-000000002907` · Offering: `TEC-INT-ECOM` · Giá W12: **8.000.000 đồng/lần**
- **KH:** Anh có đội IT nội bộ, muốn tự kết nối sàn. Bên em có hỗ trợ phần khó thôi không?
- **NV:** Dạ anh, bên em có dịch vụ tích hợp sàn và hỗ trợ kỹ thuật theo yêu cầu. Đội anh đã làm đến bước nào rồi ạ?
- **KH:** Đã lấy được đơn, nhưng đồng bộ tồn kho hai chiều chưa ổn.
- **NV:** Dạ vậy mình nên tập trung vào luồng tồn kho trước. Anh có sơ đồ dữ liệu hoặc mô tả trường hợp tồn kho lệch không? Nhớ bỏ thông tin nhạy cảm giúp em.
- **KH:** Anh không muốn mua nguyên gói tích hợp nếu chỉ cần sửa một đoạn.
- **NV:** Dạ em hiểu ý anh. Em sẽ nhờ kỹ thuật xem phần việc còn lại có thể hỗ trợ riêng theo phạm vi hay không, rồi báo phương án và chi phí tương ứng để anh chọn.
- **KH:** Ok, anh gửi mô tả lỗi.

## TECH-036 — Khách cần CRM Basic nhưng chưa tin dữ liệu an toàn
- Split: **dev** · Link: `LEAD` `0052fbf3-049c-e351-b04b-b0534c9c3f48` · Offering: `CRM_BASIC` · Giá W12: **1.900.000 đồng/gói/tháng**
- **KH:** Mình muốn dùng CRM Basic, nhưng lo nhất là thông tin khách bị lộ. Bên bạn bảo vệ dữ liệu thế nào?
- **NV:** Dạ đây là điều nên làm rõ trước khi dùng. Bạn đang quan tâm đến quyền truy cập của nhân viên, sao lưu dữ liệu hay cả hai?
- **KH:** Cả hai. Mình có danh sách khách thân thiết, mất là ảnh hưởng lớn.
- **NV:** Mình sẽ nhờ bộ phận phụ trách cung cấp thông tin đã xác nhận về phân quyền, lưu trữ và sao lưu của gói Basic. Mình không muốn hứa các cơ chế bảo mật cụ thể khi chưa có tài liệu.
- **KH:** Có tài liệu thì gửi mình đọc trước nhé.
- **NV:** Dạ được. Mình sẽ tổng hợp các câu hỏi của bạn để bên chuyên môn trả lời bằng thông tin rõ ràng, rồi bạn cân nhắc tiếp.
- **KH:** Ok, có tài liệu mình mới quyết.

## TECH-037 — App mobile cần tương thích hệ thống cũ
- Split: **dev** · Link: `LEAD` `0080af71-204f-015d-b739-baa96adef11e` · Offering: `TEC-DEV-MOBILE` · Giá W12: **85.000.000 đồng/dự án**
- **KH:** Mình muốn làm app mới nhưng dữ liệu đang ở phần mềm cũ. Bên bạn có xử lý được không?
- **NV:** Dạ bên mình có dịch vụ phát triển app mobile và di chuyển dữ liệu. Bạn đang dùng phần mềm nào, dữ liệu cần giữ gồm những gì?
- **KH:** Danh sách khách, lịch sử đơn và ghi chú. Mình không muốn nhập lại.
- **NV:** Vậy cần kiểm tra phần mềm cũ xuất được những trường nào và app mới cần dùng dữ liệu ra sao. Bạn có người quản trị hệ thống cũ để trao đổi kỹ thuật không?
- **KH:** Có. Mình sợ sau này báo thêm tiền chuyển dữ liệu.
- **NV:** Mình sẽ nhờ đội dự án tách rõ phần làm app, phần kết nối hoặc chuyển dữ liệu và chi phí tương ứng nếu có trước khi bạn quyết định. Chưa có phạm vi thì bên mình chưa thể chốt tổng chi phí.
- **KH:** Được, mình nối bạn với người quản trị.

## TECH-038 — Lead cân nhắc Enterprise không giới hạn user
- Split: **dev** · Link: `LEAD` `0088ef6f-c585-9d50-a081-76e768f12f88` · Offering: `TEC-SAS-ENTERPRISE` · Giá W12: **19.900.000 đồng/tháng**
- **KH:** Bên anh tăng nhân sự nhanh, đang xem Enterprise unlimited. Unlimited là không giới hạn người dùng thật à em?
- **NV:** Dạ tên gói Enterprise ghi unlimited user anh nhé. Anh dự kiến quy mô khoảng bao nhiêu người và có chia nhiều đơn vị không ạ?
- **KH:** Khoảng 500 người, chia năm đơn vị. Anh sợ có điều kiện ẩn.
- **NV:** Dạ, em sẽ nhờ đội sản phẩm xác nhận bằng văn bản điều kiện áp dụng số user và các giới hạn khác nếu có, nhất là mô hình năm đơn vị của anh.
- **KH:** Gói này giá bao nhiêu?
- **NV:** Enterprise niêm yết 19.900.000 đồng/tháng anh nhé. Em sẽ gửi cùng phần điều kiện sử dụng đã xác minh để anh trình ban giám đốc, không chỉ gửi mỗi giá.
- **KH:** Ừ, anh cần bản đó.

## TECH-039 — Premium 24/7 nhưng khách hỏi SLA
- Split: **dev** · Link: `LEAD` `009c7415-5df6-f15d-bca7-d69a2fd39007` · Offering: `TEC-SUP-PREMIUM` · Giá W12: **6.500.000 đồng/tháng**
- **KH:** Bên em có hỗ trợ kỹ thuật ban đêm không? Hệ thống anh chạy cả đêm.
- **NV:** Dạ bên em có gói Premium hỗ trợ 24/7 anh nhé. Anh cần hỗ trợ khi hệ thống ngừng hoạt động hay cả những vấn đề thông thường ngoài giờ?
- **KH:** Chủ yếu khi ngừng hoạt động. Gọi là có người xử lý ngay được không?
- **NV:** Em cần xác nhận thời gian tiếp nhận và phản hồi theo điều khoản cụ thể anh nhé. 24/7 là thông tin về thời gian hỗ trợ, chưa đủ để hứa khắc phục ngay mọi sự cố.
- **KH:** Vậy giá Premium thế nào?
- **NV:** Premium niêm yết 6.500.000 đồng/tháng. Em sẽ nhờ bộ phận phụ trách gửi điều kiện hỗ trợ và SLA áp dụng để anh xem có đáp ứng yêu cầu vận hành ban đêm không.
- **KH:** Ừ, anh cần đọc điều khoản trước.

## TECH-040 — Di chuyển dữ liệu, khách lo mất lịch sử
- Split: **dev** · Link: `LEAD` `00a0d47f-6cfb-6556-be7a-f1d6fb4a085e` · Offering: `TEC-IMP-MIGRATION` · Giá W12: **8.000.000 đồng/dự án**
- **KH:** Chị muốn chuyển CRM nhưng có lịch sử chăm khách năm năm. Mất ghi chú của sales là không được đâu em.
- **NV:** Dạ, vậy mình cần kiểm tra khả năng giữ lại ghi chú và lịch sử trước khi chuyển dữ liệu thật chị nhé. Phần mềm cũ có xuất dữ liệu ra file được không ạ?
- **KH:** Xuất Excel được, nhưng ghi chú nằm nhiều cột lắm.
- **NV:** Chị gửi em file mẫu đã thay dữ liệu khách bằng dữ liệu giả nhé. Em nhờ kỹ thuật kiểm tra cách ghép cột và phần nào có nguy cơ không chuyển đủ.
- **KH:** Có cách nào làm thử trước không?
- **NV:** Em sẽ hỏi đội dự án về phương án kiểm tra trên dữ liệu mẫu và cách đối chiếu kết quả trước khi triển khai chính thức. Chưa đánh giá xong thì em không hứa giữ được toàn bộ lịch sử.
- **KH:** Ừ, chị chuẩn bị mẫu dữ liệu.

## TECH-041 — Lead hỏi hỗ trợ hotline 8-5
- Split: **test** · Link: `LEAD` `00b8a92f-cd3a-8b56-aa83-e3d6495b3f00` · Offering: `TEC-SUP-STANDARD` · Giá W12: **3.000.000 đồng/tháng**
- **KH:** Bên anh làm từ 8 giờ đến 5 giờ, cần hotline hỗ trợ khi phần mềm lỗi. Bên em có gói nào?
- **NV:** Dạ có gói Standard với kênh hotline 8-5 anh nhé. Bên anh thường chỉ làm giờ hành chính hay có ca tối nữa ạ?
- **KH:** Thỉnh thoảng có tăng ca, nhưng không thường xuyên.
- **NV:** Vậy em sẽ nhờ bộ phận phụ trách xác nhận điều kiện hỗ trợ ngoài giờ của Standard để anh biết giới hạn. Nếu cần hỗ trợ thường xuyên ban đêm thì bên em còn gói Premium 24/7.
- **KH:** Hai gói đó bao nhiêu?
- **NV:** Standard niêm yết 3.000.000 đồng/tháng, Premium 6.500.000 đồng/tháng anh nhé. Em gửi anh thông tin về kênh và thời gian hỗ trợ đã xác nhận, còn SLA cụ thể sẽ kiểm tra thêm.
- **KH:** Ok, anh xem lịch tăng ca rồi quyết.

## TECH-042 — Lead hỏi tích hợp thanh toán
- Split: **test** · Link: `LEAD` `00e01a9d-94d1-e85c-955c-ac85214860f6` · Offering: `TEC-INT-PAYMENT` · Giá W12: **5.000.000 đồng/lần**
- **KH:** Chào bạn, website mình muốn cho khách thanh toán bằng QR. Bên bạn làm tích hợp được không?
- **NV:** Chào bạn, bên mình có dịch vụ tích hợp cổng thanh toán. Website bạn đang dùng nền tảng nào và đã chọn bên cung cấp thanh toán chưa?
- **KH:** Chưa chọn. Mình tưởng chỉ cần hiện mã QR là xong.
- **NV:** Nếu muốn tự xác nhận đơn đã thanh toán thì cần xem luồng giao dịch và bên cung cấp cổng thanh toán nữa. Bạn muốn chỉ hiện QR hay tự cập nhật trạng thái thanh toán vào đơn?
- **KH:** Muốn tự cập nhật luôn. Vậy chi phí sao?
- **NV:** Dịch vụ tích hợp cổng thanh toán niêm yết 5.000.000 đồng/lần. Phí giao dịch của nhà cung cấp và phạm vi kết nối cụ thể cần làm rõ riêng trước khi báo giá chốt cho bạn.
- **KH:** Ok, mình tìm bên thanh toán rồi trao đổi tiếp.

## TECH-043 — Lead hỏi Analytics Enterprise
- Split: **test** · Link: `LEAD` `0117348d-5066-f453-b459-cec67d88c4d6` · Offering: `AN_ENT` · Giá W12: **8.900.000 đồng/gói/tháng**
- **KH:** Sếp anh đang xem Analytics Enterprise. Dữ liệu bên anh ở MISA, có lấy sang báo cáo được không?
- **NV:** Dạ bên em có Analytics Enterprise và dịch vụ tích hợp ERP. Anh đang dùng MISA phiên bản nào và cần báo cáo gì nhất ạ?
- **KH:** Doanh thu, công nợ. Nhưng ngân sách anh chỉ duyệt tiền gói tháng.
- **NV:** Vậy em cần nhờ đội giải pháp kiểm tra liệu nguồn MISA của anh có cần hạng mục tích hợp riêng không. Em chưa muốn nói chỉ mua gói Analytics là có thể kết nối ngay.
- **KH:** Nếu phải trả thêm thì anh cần biết từ đầu.
- **NV:** Dạ, em sẽ đề nghị tách chi phí gói Analytics và phần tích hợp nếu cần, ghi rõ phạm vi từng khoản để anh trình lại sếp trước khi quyết.
- **KH:** Ừ, anh gửi thông tin MISA đang dùng.

## TECH-044 — Lead muốn Analytics Starter
- Split: **test** · Link: `LEAD` `01721d7f-0608-cb58-b5b4-6a6fe051df71` · Offering: `AN_START` · Giá W12: **2.900.000 đồng/gói/tháng**
- **KH:** Chị chỉ cần xem doanh thu theo sản phẩm thôi, Analytics Starter có làm được không?
- **NV:** Dạ để xác nhận đúng, em cần hỏi doanh thu đang lấy từ phần mềm bán hàng nào chị nhé. Tính năng báo cáo theo sản phẩm của Starter em sẽ kiểm tra với đội sản phẩm.
- **KH:** Bên chị dùng phần mềm bán hàng riêng, cuối ngày xuất Excel.
- **NV:** Dạ, em ghi nguồn dữ liệu là file Excel. Em sẽ kiểm tra khả năng đưa dữ liệu vào báo cáo và xem có cần thiết kế riêng không, tránh tư vấn chị mua thừa.
- **KH:** Nếu dùng Starter thì bao nhiêu một tháng?
- **NV:** Analytics Starter niêm yết 2.900.000 đồng/tháng chị nhé. Em sẽ gửi chị xác nhận về báo cáo theo sản phẩm và chi phí bổ sung nếu thực sự cần, rồi chị quyết cũng chưa muộn.
- **KH:** Ừ, chị chỉ cần đúng báo cáo đó.

## TECH-045 — Lead so sánh triển khai Standard
- Split: **test** · Link: `LEAD` `017bdb27-0012-5c5f-b4c4-976b069d0616` · Offering: `TEC-IMP-STANDARD` · Giá W12: **12.000.000 đồng/dự án**
- **KH:** Công ty anh có hai phòng ban, muốn triển khai phần mềm chung. Gói triển khai tiêu chuẩn có phù hợp không?
- **NV:** Dạ em cần biết hai phòng ban làm những quy trình gì và có dữ liệu cũ cần chuyển không anh nhé.
- **KH:** Kinh doanh với chăm sóc khách. Dữ liệu khách đang nằm ở Excel.
- **NV:** Dạ, em ghi hai phòng ban và dữ liệu Excel. Em sẽ nhờ đội triển khai xem phạm vi thiết lập và chuyển dữ liệu cần những bước nào.
- **KH:** Anh chưa có sơ đồ quy trình, vậy có tư vấn được không?
- **NV:** Được anh. Anh cứ kể cách nhân viên nhận khách, tư vấn và bàn giao cho chăm sóc khách hiện nay. Em tổng hợp thành yêu cầu để đội dự án đánh giá.
- **KH:** Ok, anh mô tả qua tin nhắn.

## TECH-046 — Lead chưa hiểu Analytics Enterprise
- Split: **test** · Link: `LEAD` `01aed1d6-fe40-1d5b-937f-0fef78f271ee` · Offering: `AN_ENT` · Giá W12: **8.900.000 đồng/gói/tháng**
- **KH:** Mình thấy Analytics Starter với Enterprise mà chẳng biết chọn cái nào.
- **NV:** Mình đang cần xem những chỉ số nào nhất? Mình hỏi để tư vấn đúng nhu cầu chứ không chọn theo tên gói nhé.
- **KH:** Doanh thu, chi phí quảng cáo và tỷ lệ chuyển đổi.
- **NV:** Dạ, ba chỉ số đó đang nằm trong cùng hệ thống hay mỗi bên một nguồn dữ liệu?
- **KH:** Doanh thu ở phần mềm bán hàng, quảng cáo ở nền tảng khác.
- **NV:** Vậy mình cần kiểm tra khả năng kết nối nguồn dữ liệu và tính năng của từng gói. Mình sẽ nhờ đội sản phẩm đối chiếu rồi gửi bạn so sánh cụ thể, không khuyên lên Enterprise khi chưa rõ cần gì.
- **KH:** Ok, mình đợi bản so sánh.

## TECH-047 — Lead hỏi VPS 8GB
- Split: **test** · Link: `LEAD` `01d02a9b-9d00-6351-b001-7cdcda302002` · Offering: `TEC-CLD-HOSTING` · Giá W12: **2.500.000 đồng/tháng**
- **KH:** Bên em có VPS 8GB không? Chị muốn chuyển website bán hàng sang.
- **NV:** Dạ có dịch vụ hosting cloud VPS 8GB RAM chị nhé. Website mình đang chạy nền tảng gì, và có lúc nào truy cập tăng đột biến không ạ?
- **KH:** Có, mỗi đợt livestream là đông. Chị không biết thông số kỹ thuật đâu.
- **NV:** Không sao chị. Chị cho em tên nền tảng website và liên hệ bên đang quản trị web, em nhờ kỹ thuật hỏi những thông số cần thiết để đánh giá khả năng đáp ứng.
- **KH:** Gói VPS đó giá sao em?
- **NV:** Giá niêm yết là 2.500.000 đồng/tháng chị nhé. Còn cấu hình chi tiết và khả năng chịu tải lúc livestream phải kiểm tra trước khi em tư vấn chị chuyển.
- **KH:** Ừ, chị hỏi bên làm web rồi gửi.

## TECH-048 — Lead hỏi triển khai cơ bản có bao gồm đào tạo
- Split: **test** · Link: `LEAD` `01d0e947-fab8-d058-ab54-8b0fd00fdd71` · Offering: `TEC-IMP-BASIC` · Giá W12: **5.000.000 đồng/dự án**
- **KH:** Anh muốn triển khai phần mềm cho cửa hàng, nhưng nhân viên lớn tuổi ít dùng máy tính. Bên em có hướng dẫn không?
- **NV:** Dạ bên em có dịch vụ đào tạo người dùng anh nhé. Mình có khoảng bao nhiêu người cần hướng dẫn, và anh muốn học trực tiếp hay trực tuyến ạ?
- **KH:** Tám người, anh muốn có người chỉ trực tiếp từng bước.
- **NV:** Dạ em ghi tám người và nhu cầu hướng dẫn trực tiếp. Em sẽ nhờ bộ phận triển khai xác nhận hình thức đào tạo phù hợp và phạm vi nội dung.
- **KH:** Nếu mua triển khai cơ bản thì đã có đào tạo chưa?
- **NV:** Em cần kiểm tra phần đào tạo có nằm trong gói triển khai cơ bản không anh nhé. Bên em có hạng mục đào tạo riêng, nhưng em sẽ làm rõ trước để anh không phải trả trùng phần việc.
- **KH:** Ừ, báo anh gộp rõ ràng là được.

## TECH-049 — Lead muốn tích hợp API nhưng chưa có tài liệu
- Split: **test** · Link: `LEAD` `01f5d9e7-8520-375e-8968-fae88c170c1c` · Offering: `TEC-INT-API` · Giá W12: **6.000.000 đồng/lần**
- **KH:** Chị bên nhân sự, muốn danh sách nhân viên tự cập nhật từ phần mềm chấm công sang hệ thống khác. Bên em làm được không?
- **NV:** Dạ bên em có dịch vụ tích hợp API. Chị đang dùng phần mềm chấm công nào ạ?
- **KH:** Chị không nhớ tên, cái đó IT quản lý. Chị chỉ muốn khỏi nhập nhân viên hai lần.
- **NV:** Dạ chị chỉ cần nói rõ dữ liệu muốn đồng bộ như mã nhân viên, phòng ban và trạng thái làm việc. Phần tên phần mềm và tài liệu kỹ thuật mình nhờ IT hỗ trợ sau cũng được.
- **KH:** Chị sợ hỏi IT nhiều quá, họ bận lắm.
- **NV:** Em sẽ soạn ngắn danh sách thông tin kỹ thuật thật sự cần để chị chuyển một lần cho IT. Khi có thông tin, bên em mới đánh giá được khả năng kết nối cụ thể.
- **KH:** Ừ, gửi chị danh sách ngắn nhé.

## TECH-050 — Lead cần dashboard một trang
- Split: **test** · Link: `LEAD` `029c6e1e-0690-5351-bc6b-3b18e7528b02` · Offering: `TEC-DEV-REPORT` · Giá W12: **5.000.000 đồng/báo cáo**
- **KH:** Em ơi, chị muốn báo cáo cho giám đốc gói trong một trang thôi, bên em có làm không?
- **NV:** Dạ bên em có dịch vụ thiết kế dashboard chị nhé. Giám đốc muốn xem những chỉ số nào trên trang đó ạ?
- **KH:** Doanh số từng nhóm và số khách quay lại. Chị không muốn nhiều biểu đồ.
- **NV:** Dạ, em ghi hai chỉ số và ưu tiên bố cục gọn. Doanh số với khách quay lại hiện chị lấy từ đâu ạ?
- **KH:** Doanh số trong phần mềm bán hàng, khách quay lại chị tự lọc Excel.
- **NV:** Vậy em sẽ nhờ đội giải pháp kiểm tra cách ghép hai nguồn dữ liệu và xác nhận phạm vi thiết kế. Chị gửi em mẫu báo cáo đã thay thông tin khách bằng dữ liệu giả nhé.
- **KH:** Được, chị gửi mẫu để bên em xem trước.

