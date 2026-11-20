# W13 AI-05 — LOGISTICS | 50 hội thoại biên tập V1

> Nguồn mapping và giá: audit W12 canonical Logistics do Codex cung cấp. Đây là **hội thoại giả lập để huấn luyện AI hỗ trợ nhân viên**, không phải bằng chứng vận đơn hay giao dịch đã xảy ra.

## Quy tắc biên tập

- Giọng nói người thật: xưng hô linh hoạt, khách có lúc gấp, bực, phân vân; nhân viên chủ động tư vấn nhưng không hứa quá dữ liệu.
- Giá là **đơn giá niêm yết W12**, không mặc nhiên là tổng cước cuối; chưa xác minh phụ phí, tuyến, điều kiện tiếp nhận, lịch xe, trạng thái vận đơn, SLA, COD và bồi thường.
- Không tạo mã vận đơn giả làm dữ kiện thực tế; khách có thể nói đã gửi mã qua chat nhưng không tự bịa mã cụ thể.
- Không dùng LOG-TRK-API (INACTIVE), LOG-TRK-REPORT (DRAFT).
- Mỗi hội thoại liên kết **một offering chính** theo W12; các dịch vụ khác nếu nhắc đến chỉ là ngữ cảnh, không tạo mapping mới.
- Split 40 dev / 10 test; 35 Customer ACTIVE / 15 Lead NEW hoặc QUALIFIED.

## Hội thoại

### LOG-001 — Đối soát COD

- Entity: Customer
- Entity UUID: `62f6f1a6-0a7e-57c9-82b1-66442a3e24f1`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-DOM-COD`
- W12 price: `15.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Shop em gửi COD đều mà tuần này có khoản chưa thấy về, chị kiểm tra giúp em với.

**NV:** Dạ em chào chị. Chị gửi giúp em mã đơn và kỳ đối soát đang thắc mắc nhé, em kiểm tra đúng khoản cho mình.

**KH:** Ba đơn cuối tuần trước đó em, kế toán cứ hỏi hoài.

**NV:** Dạ em hiểu, để em đối chiếu từng đơn với thông tin thu hộ. Chị có bảng kê đang xem thì gửi kèm giúp em nha.

**KH:** Ừ chị gửi rồi. Có gì nhắn lại sớm cho chị nhé.

**NV:** Dạ em nhận được rồi, em chuyển đối soát kiểm tra và báo lại kết quả cụ thể cho chị ạ.

---

### LOG-002 — Gửi lô hàng nhỏ

- Entity: Customer
- Entity UUID: `28739c51-0136-53e6-8ab9-5196a887d311`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-LTL-500`
- W12 price: `800.000 VND/chuyến`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Anh có khoảng 400 ký linh kiện, bên em có nhận vận chuyển ghép không?

**NV:** Dạ có dịch vụ vận tải hàng lẻ anh nhé. Anh cho em xin điểm lấy, điểm giao và kích thước kiện để tư vấn đúng chuyến.

**KH:** Lấy ở Bình Dương, giao về Đồng Nai. Hàng đóng pallet rồi.

**NV:** Dạ, nhóm dịch vụ hàng lẻ đến 500 kg bên em niêm yết 800.000đ/chuyến. Em cần kiểm tra tuyến và điều kiện nhận lô này trước khi chốt báo giá thực tế ạ.

**KH:** Ok, anh gửi kích thước qua Zalo.

**NV:** Dạ anh gửi giúp em, em sẽ đối chiếu rồi báo phương án phù hợp nha anh.

---

### LOG-003 — Tư vấn thủ tục hải quan

- Entity: Customer
- Entity UUID: `2bc67ac2-a885-5ebc-85ae-2cebc17c46cb`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-CUS-CONSULT`
- W12 price: `500.000 VND/giờ`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Em ơi, bên chị nhập lô máy móc lần đầu, giấy tờ rối quá.

**NV:** Dạ chị đang cần tư vấn hồ sơ nhập khẩu đúng không ạ? Chị cho em biết loại máy và khâu nào đang vướng nhé.

**KH:** Chị chưa biết cần chuẩn bị gì trước khi hàng về.

**NV:** Dạ bên em có dịch vụ tư vấn hải quan 500.000đ/giờ. Phần danh mục chứng từ cụ thể em cần kiểm tra theo mặt hàng của chị.

**KH:** Vậy chị gửi mô tả máy trước nha.

**NV:** Dạ được chị, em nhận thông tin rồi trao đổi lại nội dung cần tư vấn cho mình ạ.

---

### LOG-004 — Hỏi kho premium

- Entity: Customer
- Entity UUID: `8d3025a3-b8e8-542b-8de3-bb986395289e`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-WH-PREMIUM`
- W12 price: `14.000.000 VND/tháng`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Bên anh muốn thuê kho để gom hàng trước khi chia cho đại lý.

**NV:** Dạ em chào anh. Anh cần lưu hàng khoảng bao lâu và dự kiến lượng hàng thế nào ạ?

**KH:** Tầm vài tháng, có ngày xuất nhiều ngày xuất ít.

**NV:** Dạ bên em có gói kho Premium niêm yết 14 triệu/tháng. Em sẽ hỏi thêm nhu cầu xuất nhập và vị trí kho mong muốn để kiểm tra phạm vi phù hợp.

**KH:** Anh cần người theo dõi xuất nhập luôn, có được không?

**NV:** Phần vận hành cụ thể em cần xác nhận với bộ phận kho trước khi báo anh, tránh để mình hiểu nhầm quyền lợi gói ạ.

---

### LOG-005 — Đơn hoàn chưa rõ

- Entity: Customer
- Entity UUID: `825fa14a-8d5a-5653-8682-43ffbded1c6e`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-DOM-RETURN`
- W12 price: `30.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Đơn khách trả về mà hai hôm rồi tôi chưa thấy thông tin gì, sao vậy em?

**NV:** Dạ em xin lỗi vì anh phải chờ. Anh gửi giúp em mã đơn để em kiểm tra tình trạng hoàn nhé.

**KH:** Mã anh gửi hôm qua rồi, em xem lại tin nhắn trên.

**NV:** Dạ em thấy thông tin anh đã gửi. Em sẽ đối chiếu lại tiến độ xử lý với bộ phận phụ trách, không để anh phải nhập lại từ đầu.

**KH:** Anh chỉ cần biết hàng đang ở đâu thôi.

**NV:** Dạ em hiểu, em sẽ báo anh trạng thái xác minh được và bước tiếp theo ngay khi có kết quả.

---

### LOG-006 — Gửi tiết kiệm

- Entity: Customer
- Entity UUID: `37e12036-0a0e-522b-8083-85e9d6c965a8`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-DOM-ECONOMY`
- W12 price: `18.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Shop ơi gửi kiện quần áo nhỏ thì có gói nào tiết kiệm không?

**NV:** Dạ bên em có dịch vụ vận chuyển tiết kiệm, giá niêm yết 18.000đ/đơn. Bạn gửi từ đâu đến đâu vậy ạ?

**KH:** Từ quận 7 đi Thủ Đức, một túi đồ thôi.

**NV:** Dạ mình ghi nhận tuyến đó. Mình kiểm tra điều kiện áp dụng và thông tin kiện hàng rồi báo bạn mức cước phù hợp nhé.

**KH:** Oke, cần gì nữa bạn?

**NV:** Bạn cho mình cân nặng ước tính và địa chỉ nhận cụ thể là được nha.

---

### LOG-007 — Gửi nhanh Sài Gòn Hà Nội

- Entity: Customer
- Entity UUID: `1a2d7a8a-7afb-5b99-8fc2-c8ea9b3ff92e`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `EXPRESS_SGN_HN`
- W12 price: `65.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Anh cần gửi hồ sơ từ Sài Gòn ra Hà Nội, gấp lắm em.

**NV:** Dạ em chào anh. Bên em có dịch vụ chuyển phát nhanh SGN–HN niêm yết 65.000đ/đơn. Anh cần bên nhận vào thời điểm nào ạ?

**KH:** Sáng kia có cuộc họp, không biết kịp không.

**NV:** Dạ em hiểu lịch của anh quan trọng. Em kiểm tra khả năng tiếp nhận và thời gian dự kiến theo địa chỉ cụ thể, chưa dám hẹn chắc khi chưa có lịch vận chuyển.

**KH:** Ừ em xem giúp anh, trễ là không được.

**NV:** Dạ anh cho em xin điểm nhận và giờ mình có thể bàn giao hồ sơ nhé.

---

### LOG-008 — Fulfillment đơn tăng

- Entity: Customer
- Entity UUID: `6d3029ea-18b5-5719-82f5-e361dcd4b696`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-WH-FULFILLMENT`
- W12 price: `5.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Bên chị bán online, tháng này đơn tăng mà kho đóng gói không xuể.

**NV:** Dạ chị muốn tìm dịch vụ xử lý đơn tại kho đúng không ạ? Bên mình trung bình bao nhiêu đơn mỗi ngày?

**KH:** Khoảng trăm đơn, hôm sale còn nhiều hơn.

**NV:** Dạ bên em có hạng mục fulfillment niêm yết 5.000đ/đơn. Em cần làm rõ khâu chị muốn thuê và quy mô đơn trước khi tư vấn.

**KH:** Có xử lý cả hàng trả về không em?

**NV:** Phần đó em sẽ kiểm tra phạm vi dịch vụ với kho rồi báo chị riêng, để mình tính đúng nhu cầu nha.

---

### LOG-009 — Theo dõi lô hàng

- Entity: Customer
- Entity UUID: `8013452d-99a5-58ba-83d2-90f0e20065f2`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-TRK-BASIC`
- W12 price: `500.000 VND/tháng`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Em ơi, anh muốn xem trạng thái các lô hàng theo tháng, có dịch vụ không?

**NV:** Dạ có gói theo dõi cơ bản niêm yết 500.000đ/tháng anh nhé. Anh đang cần theo dõi cho bao nhiêu lô?

**KH:** Tầm 20 lô, bên anh hay phải gọi hỏi từng đơn.

**NV:** Dạ vậy em ghi nhận nhu cầu theo dõi tập trung. Em kiểm tra hình thức hiển thị và dữ liệu hỗ trợ của gói rồi tư vấn kỹ hơn.

**KH:** Anh muốn xem trên điện thoại nữa.

**NV:** Dạ em sẽ hỏi rõ khả năng sử dụng trên thiết bị của anh trước khi xác nhận nhé.

---

### LOG-010 — Kho ngắn hạn

- Entity: Customer
- Entity UUID: `aae11b4e-3c8b-5e35-8618-6fa0df1a3ed4`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `WH_SHORT`
- W12 price: `180.000 VND/m3/tháng`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Chị có ít hàng trưng bày cần gửi kho một tháng, bên em tính sao?

**NV:** Dạ bên em có dịch vụ lưu kho ngắn hạn, giá 180.000đ/m³/tháng. Chị ước tính thể tích hàng khoảng bao nhiêu ạ?

**KH:** Chắc 3 khối, có vài thùng dễ móp.

**NV:** Nếu tính riêng đơn giá niêm yết cho 3 m³ thì là 540.000đ/tháng. Em cần kiểm tra thêm điều kiện lưu trữ phù hợp với hàng của chị.

**KH:** Có cần đóng lại thùng không?

**NV:** Dạ chị gửi em ảnh tình trạng đóng gói, em hỏi kho rồi tư vấn cách tiếp nhận nhé.

---

### LOG-011 — Hàng đặc thù

- Entity: Customer
- Entity UUID: `48522b3d-4068-5fbe-86d6-d80a6c87f0d7`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-HAZMAT`
- W12 price: `5.800.000 VND/chuyến`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Bên anh cần chuyển mấy thùng hóa chất công nghiệp, có nhận không?

**NV:** Dạ anh cho em xin tên hàng và tài liệu mô tả tính chất trước nhé. Loại hàng này cần kiểm tra điều kiện tiếp nhận kỹ ạ.

**KH:** Có nhãn cảnh báo, anh gửi bảng thông tin sản phẩm được.

**NV:** Dạ em nhận để bộ phận chuyên trách đánh giá. Bên em có dịch vụ vận tải hàng nguy hiểm 5.800.000đ/chuyến, nhưng chưa thể xác nhận lô của anh đủ điều kiện.

**KH:** Anh cần báo sớm để lên lịch sản xuất.

**NV:** Dạ em hiểu, em sẽ ưu tiên chuyển thông tin để kiểm tra phạm vi nhận hàng rồi báo anh.

---

### LOG-012 — Kho ngoại quan

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006522`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-WH-BONDED`
- W12 price: `18.000.000 VND/tháng`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Anh đang tìm kho ngoại quan, bên em có gói nào?

**NV:** Dạ có gói kho ngoại quan niêm yết 18 triệu/tháng. Anh dự kiến lưu nhóm hàng gì và cần hỗ trợ khâu nào ạ?

**KH:** Hàng nhập chờ phân phối, thủ tục bên anh tự làm.

**NV:** Dạ em ghi nhận. Em sẽ đối chiếu nhu cầu lưu kho với điều kiện tiếp nhận và phạm vi dịch vụ trước khi gửi thông tin chi tiết.

**KH:** Bên em có kho ở gần cảng không?

**NV:** Vị trí kho cụ thể em cần kiểm tra lại anh nhé. Anh cho em biết cảng mình dự kiến sử dụng được không ạ?

---

### LOG-013 — Tờ khai nhập khẩu

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006523`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-CUS-IMPORT-P`
- W12 price: `2.800.000 VND/tờ khai`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Em ơi, bên chị muốn thuê khai báo nhập khẩu cho lô hàng mới.

**NV:** Dạ chị cho em xin loại hàng và tình trạng hồ sơ hiện tại để xem gói phù hợp nhé.

**KH:** Chị có invoice với packing list rồi, còn phần khai thì chưa làm.

**NV:** Dạ dịch vụ khai nhập khẩu nâng cao đang niêm yết 2.800.000đ/tờ khai. Em sẽ kiểm tra hồ sơ và phạm vi xử lý cụ thể trước khi xác nhận.

**KH:** Chị muốn gửi chứng từ trước có được không?

**NV:** Dạ chị gửi qua kênh làm việc của mình nhé, nhớ che thông tin nhạy cảm không cần thiết. Em chuyển bộ phận phụ trách xem giúp chị.

---

### LOG-014 — Kiểm kê kho lệch số

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006525`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-WH-INVENTORY`
- W12 price: `3.500.000 VND/lần`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Số tồn kho bên chị đang lệch, kiểm tra mấy lần vẫn không ra.

**NV:** Dạ em chào chị. Chị đang muốn kiểm kê lại toàn bộ hay một nhóm mã hàng trước ạ?

**KH:** Nhóm hàng mỹ phẩm thôi, sợ xuất nhầm cho đại lý.

**NV:** Dạ bên em có dịch vụ kiểm kê kho 3.500.000đ/lần. Em cần hỏi phạm vi kiểm kê và cách đối chiếu dữ liệu trước khi tư vấn chính xác.

**KH:** Ừ, chị gửi danh sách mã hàng.

**NV:** Dạ em nhận danh sách rồi trao đổi lại kế hoạch kiểm tra phù hợp cho chị nhé.

---

### LOG-015 — Xe lạnh

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006528`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-COLD`
- W12 price: `4.200.000 VND/chuyến`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Anh có lô thực phẩm cần xe lạnh, bên em nhận không?

**NV:** Dạ có dịch vụ vận tải lạnh niêm yết 4.200.000đ/chuyến. Anh cho em xin tuyến đường, lượng hàng và yêu cầu bảo quản nhé.

**KH:** Khoảng một tấn, cần giữ nhiệt ổn định suốt đường.

**NV:** Dạ phần mức nhiệt và khả năng đáp ứng chuyến này em phải kiểm tra với điều vận trước, anh gửi giúp em yêu cầu cụ thể nha.

**KH:** Anh gửi bảng yêu cầu ngay.

**NV:** Dạ em nhận rồi, em sẽ phản hồi phương án khi bộ phận phụ trách xác minh xong ạ.

---

### LOG-016 — Hàng lẻ một tấn

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006529`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-LTL-1T`
- W12 price: `1.200.000 VND/chuyến`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Bên em có chuyến ghép hàng cỡ một tấn không chị?

**NV:** Dạ có gói vận tải hàng lẻ 1 tấn, giá niêm yết 1.200.000đ/chuyến. Em gửi từ kho nào đến kho nào vậy?

**KH:** Từ Long An lên Bình Dương, hàng đóng thùng.

**NV:** Dạ chị ghi nhận tuyến và loại hàng. Em cho chị thêm số kiện với kích thước để chị kiểm tra khả năng ghép chuyến nhé.

**KH:** Khoảng 40 thùng, để em đo lại.

**NV:** Ừ em, có số đo cứ gửi chị, chị tư vấn tiếp cho đúng nha.

---

### LOG-017 — Theo dõi nhiệt độ

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006530`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-TRK-TEMP`
- W12 price: `800.000 VND/tháng`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Hàng bên chị cần theo dõi nhiệt độ, có cách nào xem báo cáo không em?

**NV:** Dạ bên em có dịch vụ theo dõi nhiệt độ niêm yết 800.000đ/tháng. Chị cần theo dõi cho kho hay chuyến vận chuyển ạ?

**KH:** Cho hàng lưu kho, sợ biến động mà không biết.

**NV:** Dạ em hiểu. Em sẽ kiểm tra cách ghi nhận và cung cấp dữ liệu của dịch vụ trước khi xác nhận hình thức báo cáo cho chị.

**KH:** Nếu được thì chị muốn trao đổi với kỹ thuật.

**NV:** Dạ em ghi nhận, em chuyển nhu cầu để sắp xếp đầu mối tư vấn phù hợp nhé.

---

### LOG-018 — Thuê xe tải 10 tấn

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006531`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-FTL-10T`
- W12 price: `6.000.000 VND/chuyến`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Anh cần xe 10 tấn chở hàng từ nhà máy đi kho, bên em báo giá sao?

**NV:** Dạ gói vận tải nguyên chuyến 10 tấn niêm yết 6 triệu/chuyến anh nhé. Anh cho em xin tuyến và loại hàng để kiểm tra phương án.

**KH:** Hàng bao bì, đi trong ngày được không?

**NV:** Dạ em cần kiểm tra lịch xe và tuyến cụ thể mới xác nhận được khả năng đi trong ngày ạ.

**KH:** Ừ, anh gửi địa chỉ hai đầu.

**NV:** Dạ anh gửi giúp em, em đối chiếu với điều vận rồi báo lại nha.

---

### LOG-019 — Gia hạn lưu kho

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006532`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `WH_SHORT`
- W12 price: `180.000 VND/m3/tháng`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Chị gửi hàng kho ngắn hạn mà chắc phải để thêm, có sao không em?

**NV:** Dạ chị đang dùng dịch vụ lưu kho ngắn hạn đúng không ạ? Chị dự kiến cần thêm bao lâu?

**KH:** Thêm khoảng hai tuần, hàng chưa bán hết.

**NV:** Dạ gói lưu kho ngắn hạn đang niêm yết 180.000đ/m³/tháng. Phần gia hạn và cách tính cho thời gian thêm em cần kiểm tra điều kiện áp dụng.

**KH:** Chị sợ tự nhiên bị tính thêm nhiều.

**NV:** Dạ em sẽ hỏi rõ cách tính rồi báo chị trước khi mình quyết định nhé.

---

### LOG-020 — Khách yêu cầu hoàn hàng

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006533`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-DOM-RETURN`
- W12 price: `30.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Khách của anh từ chối nhận, giờ hàng đi đâu rồi em?

**NV:** Dạ em xin lỗi vì anh chưa có thông tin. Anh cho em mã đơn để em tra tình trạng và yêu cầu hoàn hàng nhé.

**KH:** Anh gửi mã đây, khách bảo sai màu.

**NV:** Dạ em nhận được rồi. Dịch vụ hoàn hàng có giá niêm yết 30.000đ/đơn, còn trường hợp này em sẽ kiểm tra trạng thái và điều kiện áp dụng trước.

**KH:** Anh chỉ cần biết có đang trả về kho không.

**NV:** Dạ em sẽ xác minh lộ trình thực tế rồi báo anh, không đoán trạng thái đơn ạ.

---

### LOG-021 — Hồ sơ gấp

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006534`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `EXPRESS_SGN_HN`
- W12 price: `65.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Chị ơi em gửi giấy tờ ra Hà Nội, có gói nhanh không?

**NV:** Dạ có em nha, chuyển phát nhanh SGN–HN niêm yết 65.000đ/đơn. Em gửi từ khu vực nào ở Sài Gòn?

**KH:** Em ở Gò Vấp, cần tới nơi trước thứ sáu.

**NV:** Dạ chị xem lịch nhận và thời gian dự kiến theo địa chỉ cụ thể cho em nha, rồi mình tính phương án phù hợp.

**KH:** Vậy em gửi địa chỉ người nhận.

**NV:** Ừ em, gửi chị thông tin cần thiết, chị xem phương án phù hợp nha.

---

### LOG-022 — Tem nhãn sai

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006535`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-VA-LABEL`
- W12 price: `20.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Đơn bên chị bị dán nhầm tem, khách nhận nhầm tên người khác rồi.

**NV:** Dạ em xin lỗi chị về sự bất tiện này. Chị cho em xin mã đơn và ảnh tem để em đối chiếu nhé.

**KH:** Đây em, chị sợ các đơn còn lại cũng bị.

**NV:** Dạ em ghi nhận cả lo ngại về những đơn khác. Em sẽ chuyển kiểm tra khâu dán nhãn và đối chiếu thông tin trước khi báo hướng xử lý.

**KH:** Có phải trả thêm phí dán lại không?

**NV:** Dịch vụ dán nhãn niêm yết 20.000đ/đơn, nhưng trường hợp sai sót này em cần xác minh trách nhiệm và cách xử lý, chưa thể kết luận phí cho chị ạ.

---

### LOG-023 — Tư vấn dán nhãn số lượng lớn

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006536`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-VA-LABEL`
- W12 price: `20.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Shop mình gửi 200 đơn, có bên nào dán tem giúp không?

**NV:** Dạ bên em có dịch vụ dán nhãn 20.000đ/đơn. Anh cần dán nhãn theo mẫu có sẵn hay cần hỗ trợ chuẩn bị mẫu ạ?

**KH:** Anh có mẫu rồi, chỉ cần dán đúng mã SKU.

**NV:** Dạ anh gửi em mẫu và số lượng dự kiến, em kiểm tra phạm vi thực hiện cho lô này nhé.

**KH:** Nếu ổn anh làm lâu dài luôn.

**NV:** Dạ em rất vui được hỗ trợ. Mình chốt cách làm cho lô đầu thật rõ rồi tính kế hoạch dài hạn anh nha.

---

### LOG-024 — Hồ sơ thất lạc?

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006538`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `EXPRESS_SGN_HN`
- W12 price: `65.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Em ơi, anh gửi hồ sơ đi Hà Nội mà bên nhận bảo chưa thấy. Gấp quá!

**NV:** Dạ em xin lỗi anh vì việc này làm anh lo. Anh cho em mã vận đơn và thời điểm gửi để em tra ngay nhé.

**KH:** Mã anh gửi ở trên, đừng bắt anh đọc lại nữa.

**NV:** Dạ em thấy mã rồi anh. Em sẽ chuyển kiểm tra tình trạng vận chuyển và liên hệ điểm giao liên quan.

**KH:** Hôm nay phải có câu trả lời đó em.

**NV:** Dạ em ghi nhận mức độ khẩn cấp. Có thông tin xác minh em sẽ báo anh cụ thể, không hứa trước kết quả khi chưa tra được ạ.

---

### LOG-025 — Chở nguyên xe 5 tấn

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006541`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-FTL-5T`
- W12 price: `3.500.000 VND/chuyến`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Bên chị cần xe 5 tấn cho đợt hàng mới, có nhận không?

**NV:** Dạ có dịch vụ vận tải nguyên chuyến 5 tấn, niêm yết 3.500.000đ/chuyến. Chị gửi hàng gì và đi tuyến nào ạ?

**KH:** Đồ gia dụng, từ kho ở Đồng Nai về Bình Dương.

**NV:** Dạ em ghi nhận. Chị cho em thêm ngày dự kiến lấy hàng và cách đóng kiện để em hỏi điều vận nhé.

**KH:** Chị muốn lấy chiều mai.

**NV:** Dạ em kiểm tra lịch xe rồi báo chị khả năng tiếp nhận, chưa chốt lịch khi chưa xác nhận ạ.

---

### LOG-026 — Xe 20 tấn

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006542`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-FTL-20T`
- W12 price: `9.500.000 VND/chuyến`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Anh cần báo giá chuyến xe 20 tấn, có thể gửi nhanh cho anh không?

**NV:** Dạ gói nguyên chuyến 20 tấn niêm yết 9.500.000đ/chuyến. Anh cho em tuyến và loại hàng để em xem mức giá áp dụng nhé.

**KH:** Hàng sắt thép, hai đầu đều có xe nâng.

**NV:** Dạ anh cho em thêm khối lượng thực tế và thời gian bốc dỡ dự kiến, em kiểm tra phương án vận chuyển phù hợp.

**KH:** Ok, anh gửi bảng kê.

**NV:** Dạ em nhận rồi, em đối chiếu với điều vận và báo lại anh ạ.

---

### LOG-027 — Hàng ghép bị chậm

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006543`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-LTL-500`
- W12 price: `800.000 VND/chuyến`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Em ơi lô hàng ghép của anh sao vẫn chưa tới? Khách gọi giục suốt.

**NV:** Dạ em xin lỗi anh vì bị động tiến độ. Anh gửi em mã lô và điểm nhận để em kiểm tra nhé.

**KH:** Anh đã gửi rồi, giờ anh cần biết khi nào tới.

**NV:** Dạ em thấy thông tin rồi. Em sẽ hỏi điều vận trạng thái thực tế và thời gian dự kiến cập nhật được, không báo đại cho anh.

**KH:** Anh cần thông tin để còn báo khách.

**NV:** Dạ em hiểu, em sẽ ưu tiên phản hồi phần đã xác minh để anh chủ động trao đổi với khách ạ.

---

### LOG-028 — Bảo hiểm hàng hóa

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006544`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-VA-INSURANCE`
- W12 price: `120.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Bên em gửi hàng giá trị cao, có bảo hiểm gì không chị?

**NV:** Dạ có hạng mục bảo hiểm hàng hóa niêm yết 120.000đ/đơn. Em gửi loại hàng gì và giá trị khai báo khoảng bao nhiêu?

**KH:** Là máy ảnh, em sợ va đập.

**NV:** Dạ chị ghi nhận. Phạm vi bảo hiểm, điều kiện áp dụng và mức bồi thường chị cần kiểm tra điều khoản cụ thể trước khi tư vấn nhé.

**KH:** Vậy chị gửi thông tin để em đọc trước nha.

**NV:** Ừ em, chị sẽ chuyển phần thông tin đã xác minh để em cân nhắc trước khi gửi hàng.

---

### LOG-029 — Chứng nhận xuất xứ

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006545`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-CUS-CO`
- W12 price: `800.000 VND/bộ hồ sơ`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Anh cần làm C/O cho lô hàng xuất, bên em hỗ trợ được không?

**NV:** Dạ bên em có dịch vụ C/O niêm yết 800.000đ/bộ hồ sơ. Anh cho em biết mặt hàng và thị trường xuất dự kiến nhé.

**KH:** Hàng gỗ, đi Nhật. Hồ sơ anh đang gom.

**NV:** Dạ em ghi nhận. Em sẽ nhờ bộ phận chứng từ kiểm tra loại hồ sơ phù hợp và điều kiện xử lý.

**KH:** Có thiếu giấy gì thì báo anh một lần nha.

**NV:** Dạ em sẽ tổng hợp những mục xác minh được rồi gửi anh để mình chuẩn bị thuận tiện hơn ạ.

---

### LOG-030 — Kho lạnh

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006547`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-WH-COLD`
- W12 price: `22.000.000 VND/tháng`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Chị cần kho lạnh chứa hàng thực phẩm khoảng hai tháng, có gói nào?

**NV:** Dạ có gói kho lạnh niêm yết 22 triệu/tháng. Chị cho em biết loại hàng, thể tích và nhiệt độ cần bảo quản nhé.

**KH:** Hàng đông lạnh, khoảng 10 pallet.

**NV:** Dạ em ghi nhận. Em cần kiểm tra điều kiện kho và khả năng tiếp nhận đúng mức nhiệt của chị trước khi tư vấn gói.

**KH:** Chị muốn xem kho trước được không?

**NV:** Dạ em sẽ hỏi bộ phận kho về việc sắp xếp tham quan rồi báo chị nhé.

---

### LOG-031 — Đơn tiết kiệm phát sinh

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006548`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-DOM-ECONOMY`
- W12 price: `18.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Sao đơn này trên bảng kê khác mức 18k em tư vấn trước vậy?

**NV:** Dạ em xin lỗi vì thông tin chưa rõ làm chị khó đối chiếu. Chị gửi em mã đơn và dòng phí đang thắc mắc nhé.

**KH:** Đây, có thêm một khoản mà chị không hiểu.

**NV:** Dạ em sẽ kiểm tra khoản đó phát sinh từ đâu và có áp dụng đúng trường hợp của chị không, rồi giải thích cụ thể.

**KH:** Đừng trả lời chung chung nữa nha.

**NV:** Dạ em hiểu. Em sẽ đối chiếu đúng chứng từ của đơn này trước khi phản hồi chị ạ.

---

### LOG-032 — Tem cho đơn online

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006549`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-VA-LABEL`
- W12 price: `20.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Mình bán đồ handmade, có dịch vụ dán nhãn cho đơn lẻ không?

**NV:** Dạ bên mình có dịch vụ dán nhãn, niêm yết 20.000đ/đơn. Bạn cần dán mã sản phẩm hay thông tin người nhận?

**KH:** Dán mã sản phẩm thôi, mỗi ngày vài đơn.

**NV:** Dạ mình ghi nhận số lượng. Bạn gửi mẫu nhãn để mình kiểm tra cách tiếp nhận phù hợp nhé.

**KH:** Có phải gửi hàng tới kho không?

**NV:** Phần điểm nhận và quy trình bàn giao mình sẽ hỏi bộ phận phụ trách rồi báo bạn nha.

---

### LOG-033 — Fulfillment cho sàn

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006550`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-WH-FULFILLMENT`
- W12 price: `5.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Bên anh đang bán trên hai sàn, kho xử lý đơn hơi rối.

**NV:** Dạ anh đang cần thuê khâu xử lý đơn hay muốn tư vấn thêm phần quản lý hàng trong kho ạ?

**KH:** Chủ yếu đóng đơn và bàn giao, đừng để sót đơn.

**NV:** Dạ dịch vụ fulfillment bên em niêm yết 5.000đ/đơn. Em sẽ kiểm tra cụ thể các bước trong phạm vi dịch vụ để anh cân nhắc.

**KH:** Có báo cáo đơn mỗi ngày không?

**NV:** Phần báo cáo em cần xác minh với kho trước khi xác nhận cho anh ạ.

---

### LOG-034 — Hồ sơ C/O bổ sung

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006552`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-CUS-CO`
- W12 price: `800.000 VND/bộ hồ sơ`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Em ơi, bộ hồ sơ C/O bên chị nộp rồi mà lại bị yêu cầu bổ sung, sao giờ?

**NV:** Dạ em xin lỗi chị vì đang phải chờ thêm. Chị gửi giúp em nội dung yêu cầu bổ sung để em nhờ bộ phận chứng từ xem nhé.

**KH:** Họ ghi thiếu giấy xác nhận, chị không hiểu giấy nào.

**NV:** Dạ em sẽ đối chiếu đúng thông báo và hồ sơ hiện có rồi giải thích cho chị phần cần chuẩn bị.

**KH:** Chị sợ lỡ lịch xuất hàng.

**NV:** Dạ em ghi nhận mốc thời gian của chị để bộ phận phụ trách ưu tiên xem hồ sơ nhé.

---

### LOG-035 — Khách hỏi COD cho shop mới

- Entity: Customer
- Entity UUID: `50000000-0000-5000-8000-000000006555`
- Entity status: `ACTIVE`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-DOM-COD`
- W12 price: `15.000 VND/đơn`
- Mapping evidence: `C`
- Split: `dev`

**KH:** Shop chị mới bán online, bên em có thu hộ tiền hàng không?

**NV:** Dạ bên em có dịch vụ COD, giá niêm yết 15.000đ/đơn. Chị định gửi hàng đi khu vực nào ạ?

**KH:** Chủ yếu nội thành, mỗi ngày vài đơn thôi.

**NV:** Dạ em ghi nhận. Chị cho em biết cách mình đang quản lý đơn để em tư vấn phần thu hộ và đối soát phù hợp nhé.

**KH:** Chị muốn biết tiền thu hộ về lúc nào.

**NV:** Dạ lịch đối soát cụ thể em cần kiểm tra điều kiện dịch vụ rồi báo chị chính xác nha.

---

### LOG-036 — Gửi hàng lần đầu

- Entity: Lead
- Entity UUID: `3969d098-0afd-52aa-a37e-44796e2accf9`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `EXPRESS_SGN_HN`
- W12 price: `65.000 VND/đơn`
- Mapping evidence: `L`
- Split: `dev`

**KH:** Dạ cho em hỏi gửi một kiện từ Sài Gòn ra Hà Nội thì làm sao ạ?

**NV:** Dạ chào em. Bên chị có dịch vụ chuyển phát nhanh SGN–HN niêm yết 65.000đ/đơn. Em gửi hàng gì vậy?

**KH:** Một hộp quà sinh nhật, em chưa đóng gói.

**NV:** Dạ em cho chị biết kích thước và loại đồ bên trong nhé, chị sẽ kiểm tra điều kiện nhận hàng và tư vấn cách gửi phù hợp.

**KH:** Em sợ không kịp sinh nhật bạn.

**NV:** Ừ em, em cho chị ngày cần nhận nữa nha, chị kiểm tra lịch dự kiến rồi báo rõ để em quyết định.

---

### LOG-037 — Chọn tiêu chuẩn

- Entity: Lead
- Entity UUID: `c6791f49-1f1b-57a4-8ce7-82ced04e78f3`
- Entity status: `QUALIFIED`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `STANDARD_NATIONWIDE`
- W12 price: `35.000 VND/đơn`
- Mapping evidence: `L`
- Split: `dev`

**KH:** Bên mình có gửi hàng toàn quốc không bạn?

**NV:** Dạ có dịch vụ vận chuyển tiêu chuẩn toàn quốc, niêm yết 35.000đ/đơn. Bạn định gửi tuyến nào ạ?

**KH:** Từ Cần Thơ ra Đà Nẵng, không cần quá gấp.

**NV:** Dạ mình ghi nhận. Bạn cho mình cân nặng và kích thước kiện để mình kiểm tra điều kiện nhận và cước thực tế nhé.

**KH:** Để mình cân lại rồi gửi.

**NV:** Dạ được, có số đo bạn nhắn mình nha.

---

### LOG-038 — Khách hối gửi hồ sơ

- Entity: Lead
- Entity UUID: `547e32da-9160-52c7-a9c8-68382d932899`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `EXPRESS_SGN_HN`
- W12 price: `65.000 VND/đơn`
- Mapping evidence: `L`
- Split: `dev`

**KH:** Em ơi cần chuyển gấp giấy tờ ra Hà Nội, mai nhận được không?

**NV:** Dạ em chào anh. Bên em có dịch vụ chuyển phát nhanh SGN–HN, giá niêm yết 65.000đ/đơn. Anh cho em biết điểm gửi và địa chỉ nhận nhé.

**KH:** Anh ở Tân Bình, bên nhận ở Cầu Giấy.

**NV:** Dạ em sẽ kiểm tra lịch tiếp nhận và thời gian dự kiến theo tuyến này. Em chưa thể hứa giao ngày mai khi chưa có xác nhận.

**KH:** Nhanh giúp anh nha, giấy tờ quan trọng.

**NV:** Dạ em ghi nhận mức độ gấp, em sẽ báo anh phương án xác minh được sớm nhất có thể ạ.

---

### LOG-039 — Báo giá tiêu chuẩn

- Entity: Lead
- Entity UUID: `b52f8613-14d4-5aa9-9d4d-65a9af635784`
- Entity status: `QUALIFIED`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `STANDARD_NATIONWIDE`
- W12 price: `35.000 VND/đơn`
- Mapping evidence: `L`
- Split: `dev`

**KH:** Mình có 3 kiện sách gửi cho bạn, giá 35k là cả ba kiện à?

**NV:** Dạ 35.000đ là đơn giá niêm yết theo đơn của dịch vụ tiêu chuẩn toàn quốc. Mình cần kiểm tra cách tính cho ba kiện cụ thể của bạn nhé.

**KH:** À vậy không phải cứ ba kiện là 35k hả?

**NV:** Dạ đúng rồi, mình chưa nên chốt tổng cước khi chưa có cân nặng, kích thước và quy định áp dụng cho đơn nhiều kiện.

**KH:** Oke mình chụp ba kiện gửi bạn.

**NV:** Dạ bạn gửi nhé, mình xem thông tin rồi tư vấn tiếp cho sát nhu cầu.

---

### LOG-040 — Kho một tháng

- Entity: Lead
- Entity UUID: `e7458c3e-e0c4-53ee-a982-75e4633fa1a9`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `WH_SHORT`
- W12 price: `180.000 VND/m3/tháng`
- Mapping evidence: `L`
- Split: `dev`

**KH:** Anh cần gửi hàng vào kho tầm một tháng, báo giúp anh chi phí.

**NV:** Dạ bên em có lưu kho ngắn hạn niêm yết 180.000đ/m³/tháng. Anh dự kiến cần khoảng bao nhiêu mét khối ạ?

**KH:** Chắc 5 khối, toàn hàng đóng thùng.

**NV:** Dạ tính theo đơn giá niêm yết thì 5 m³ là 900.000đ/tháng, còn điều kiện lưu kho thực tế em sẽ kiểm tra thêm theo loại hàng và địa điểm.

**KH:** Anh cần gửi vào đầu tuần tới.

**NV:** Dạ anh cho em ngày dự kiến, em hỏi kho về khả năng tiếp nhận rồi báo lại nhé.

---

### LOG-041 — Chuyển phát nhanh cho khách mới

- Entity: Lead
- Entity UUID: `374165e6-feb5-55bc-89a3-3f3c7fd76811`
- Entity status: `QUALIFIED`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `EXPRESS_SGN_HN`
- W12 price: `65.000 VND/đơn`
- Mapping evidence: `L`
- Split: `test`

**KH:** Chị ơi em cần gửi gấp một bì thư từ Sài Gòn ra Hà Nội.

**NV:** Dạ chào em. Bên chị có chuyển phát nhanh SGN–HN giá niêm yết 65.000đ/đơn. Em muốn người nhận có trước ngày nào?

**KH:** Trước cuối tuần chị ạ, em chưa gửi bên mình bao giờ.

**NV:** Dạ chị hướng dẫn em từng bước nha. Em cho chị điểm gửi, điểm nhận và thời điểm có thể bàn giao để chị kiểm tra lịch phù hợp.

**KH:** Em gửi địa chỉ luôn nhé.

**NV:** Ừ em, chị nhận thông tin rồi kiểm tra phương án cho em nha.

---

### LOG-042 — Khách muốn đổi địa chỉ

- Entity: Lead
- Entity UUID: `d04a81c3-e6e3-5133-9e9d-6b7ca7acb6a9`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `STANDARD_NATIONWIDE`
- W12 price: `35.000 VND/đơn`
- Mapping evidence: `L`
- Split: `test`

**KH:** Chị ơi em ghi nhầm địa chỉ nhận rồi, sửa được không?

**NV:** Dạ em gửi chị mã đơn với địa chỉ đúng nhé, chị kiểm tra xem đơn đang ở bước nào.

**KH:** Đơn chưa tới người nhận đâu chị, em lo quá.

**NV:** Dạ chị hiểu. Chị sẽ hỏi bộ phận vận hành khả năng cập nhật địa chỉ theo trạng thái hiện tại.

**KH:** Nếu không đổi được thì sao chị?

**NV:** Chị sẽ báo em phương án được phép thực hiện sau khi kiểm tra, mình chưa cần đoán trước nha.

---

### LOG-043 — Kho ngắn hạn cho shop

- Entity: Lead
- Entity UUID: `75ebbb7c-4d83-8153-884c-701b883c5928`
- Entity status: `QUALIFIED`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `WH_SHORT`
- W12 price: `180.000 VND/m3/tháng`
- Mapping evidence: `L`
- Split: `test`

**KH:** Mình cần thuê kho tạm vài tuần để chứa hàng sale, có không?

**NV:** Dạ có dịch vụ lưu kho ngắn hạn, giá 180.000đ/m³/tháng. Bạn ước tính khoảng bao nhiêu khối hàng?

**KH:** Chắc hai khối, nhưng ngày nào cũng lấy bớt ra.

**NV:** Dạ mình ghi nhận nhu cầu xuất hàng thường xuyên. Phần cách tính và quy trình xuất kho mình sẽ hỏi bộ phận kho trước khi tư vấn nhé.

**KH:** Oke, mình cần chỗ linh hoạt chút.

**NV:** Dạ mình hiểu, mình sẽ kiểm tra xem phương án nào phù hợp nhất với nhịp bán của bạn.

---

### LOG-044 — Gửi hàng theo chuyến

- Entity: Lead
- Entity UUID: `cc928236-a005-8857-9e3d-b5b1fbf0de95`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-FTL-5T`
- W12 price: `3.500.000 VND/chuyến`
- Mapping evidence: `L`
- Split: `test`

**KH:** Bên em cần xe 5 tấn đi giao cho đại lý, có làm theo chuyến không anh?

**NV:** Dạ có em, dịch vụ nguyên chuyến 5 tấn niêm yết 3.500.000đ/chuyến. Em gửi loại hàng gì và từ đâu đến đâu?

**KH:** Hàng tiêu dùng, giao hai điểm.

**NV:** Ừ em, anh ghi nhận hai điểm giao. Để anh kiểm tra tuyến và điều kiện vận chuyển rồi báo giá sát nhu cầu nhé.

**KH:** Em gửi danh sách địa chỉ nha.

**NV:** Ừ em, gửi anh nhé, anh đối chiếu rồi báo em.

---

### LOG-045 — Ghép chuyến một tấn

- Entity: Lead
- Entity UUID: `29ca9345-6786-865f-a01c-06e19fdde73f`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-LTL-1T`
- W12 price: `1.200.000 VND/chuyến`
- Mapping evidence: `L`
- Split: `test`

**KH:** Em hỏi chút, hàng khoảng 900 ký thì có đi ghép được không chị?

**NV:** Dạ bên chị có gói hàng lẻ 1 tấn niêm yết 1.200.000đ/chuyến. Em gửi từ đâu đến đâu và hàng gì?

**KH:** Hàng giấy, từ Bình Phước về Sài Gòn.

**NV:** Dạ chị ghi nhận. Em cho chị kích thước kiện và ngày muốn gửi để chị kiểm tra khả năng ghép chuyến nhé.

**KH:** Em muốn thứ hai.

**NV:** Dạ chị kiểm tra lịch vận chuyển rồi báo em, mình chưa chốt lịch ngay nha.

---

### LOG-046 — Đặt xe cho kho

- Entity: Lead
- Entity UUID: `ff09b34f-b74d-d156-8361-0c875f7e3153`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-FRT-FTL-10T`
- W12 price: `6.000.000 VND/chuyến`
- Mapping evidence: `L`
- Split: `test`

**KH:** Bên anh cần xe 10 tấn, em báo nhanh giúp anh.

**NV:** Dạ em chào anh. Dịch vụ nguyên chuyến 10 tấn niêm yết 6 triệu/chuyến. Anh cần đi tuyến nào ạ?

**KH:** Từ KCN Sóng Thần ra cảng, hàng trên pallet.

**NV:** Dạ em ghi nhận. Anh cho em số pallet và khung giờ cần lấy để em kiểm tra điều vận nhé.

**KH:** Anh cần lấy trước trưa.

**NV:** Dạ em sẽ hỏi lịch xe theo khung giờ đó rồi báo anh khả năng đáp ứng.

---

### LOG-047 — Chuyển hàng tiêu chuẩn

- Entity: Lead
- Entity UUID: `42fbac36-35fb-5e5c-afb8-baf703eea580`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `STANDARD_NATIONWIDE`
- W12 price: `35.000 VND/đơn`
- Mapping evidence: `L`
- Split: `test`

**KH:** Mình cần gửi mấy hộp quà đi tỉnh, bên bạn có gói nào không quá gấp không?

**NV:** Dạ có dịch vụ vận chuyển tiêu chuẩn toàn quốc, niêm yết 35.000đ/đơn. Bạn gửi từ đâu đến đâu ạ?

**KH:** Từ TP.HCM về Nha Trang, khoảng hai hộp.

**NV:** Dạ bạn cho mình cân nặng và kích thước từng hộp để mình kiểm tra cách tính cước và điều kiện nhận nhé.

**KH:** Mình đo rồi nhắn lại nha.

**NV:** Dạ được bạn, có thông tin mình tư vấn tiếp cho chính xác.

---

### LOG-048 — Fulfillment khởi động

- Entity: Lead
- Entity UUID: `0d641cf0-7bfc-365f-afb1-53be5ab8cc83`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-WH-FULFILLMENT`
- W12 price: `5.000 VND/đơn`
- Mapping evidence: `L`
- Split: `test`

**KH:** Bên mình xử lý đơn online cho shop nhỏ được không em?

**NV:** Dạ có dịch vụ fulfillment niêm yết 5.000đ/đơn. Chị dự kiến có bao nhiêu đơn mỗi ngày ạ?

**KH:** Khoảng 30 đơn, có ngày tăng gấp đôi.

**NV:** Dạ em hiểu rồi. Chị muốn hỗ trợ khâu soạn hàng, đóng gói hay bàn giao đơn để em tư vấn đúng nhu cầu ạ?

**KH:** Chị muốn thuê hết, đỡ phải tuyển thêm người.

**NV:** Dạ em hiểu nhu cầu của chị. Em sẽ hỏi rõ từng khâu có thể hỗ trợ rồi tư vấn phương án phù hợp ạ.

---

### LOG-049 — Lắp ráp bộ sản phẩm

- Entity: Lead
- Entity UUID: `dfd0607e-c779-6452-b418-87adcd31a12a`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-VA-ASSEMBLY`
- W12 price: `80.000 VND/đơn`
- Mapping evidence: `L`
- Split: `test`

**KH:** Shop em có mấy món cần ghép thành bộ trước khi gửi, bên chị làm được không?

**NV:** Dạ bên chị có dịch vụ lắp ráp/ghép bộ niêm yết 80.000đ/đơn. Em muốn ghép những món nào thành một bộ?

**KH:** Ba món mỹ phẩm, đóng thành set quà.

**NV:** Dạ em gửi chị hình mẫu set và số lượng dự kiến nhé, chị kiểm tra phạm vi thao tác phù hợp.

**KH:** Em tính làm thử ít đơn trước.

**NV:** Ừ em, mình làm rõ yêu cầu lô thử trước để tránh nhầm bộ sản phẩm nha.

---

### LOG-050 — Đóng gói chống va đập

- Entity: Lead
- Entity UUID: `1bddb255-f348-9457-812d-7a90cac0a1a0`
- Entity status: `NEW`
- Organization: `a477cbf4-afb4-5960-b165-41537f66ac13` (`log`)
- Offering: `LOG-VA-PACKAGING`
- W12 price: `50.000 VND/đơn`
- Mapping evidence: `L`
- Split: `test`

**KH:** Chị ơi em bán đồ gốm, cần đóng gói cẩn thận, bên mình có hỗ trợ không?

**NV:** Dạ có dịch vụ đóng gói niêm yết 50.000đ/đơn. Em gửi chị ảnh và kích thước món hàng để chị kiểm tra cách đóng phù hợp nhé.

**KH:** Ly gốm với bình hoa, dễ vỡ lắm chị.

**NV:** Ừ em, đồ gốm dễ vỡ nên mình cần xem kỹ cách đóng. Chị hỏi bộ phận đóng gói phương án phù hợp rồi báo em nha.

**KH:** Em gửi hình đây.

**NV:** Dạ chị nhận rồi, chị sẽ đối chiếu yêu cầu và báo em cách xử lý phù hợp nha.

---
