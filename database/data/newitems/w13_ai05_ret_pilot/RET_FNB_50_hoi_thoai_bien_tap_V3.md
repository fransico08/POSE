# W13 AI-05 — RET/F&B | 50 hội thoại biên tập V3

> **Trạng thái:** Bản biên tập nội dung để người dùng duyệt và Codex kiểm tra W12 trước khi sinh CSV. Chưa phải dữ liệu import.

## Phạm vi và quy tắc dữ liệu

- Organization: `83236f5d-2b8b-52bb-af64-ccde174487ab` (`ret`, POSE Retail, ACTIVE). Nguồn mapping và giá: báo cáo audit W12 canonical do Codex cung cấp.
- 50 hội thoại: 35 Customer ACTIVE, 15 Lead NEW/QUALIFIED; 40 dev, 10 test. `RET-050` được gán `test` theo quyết định pilot, hoàn thành split 40 dev / 10 test; split là metadata pilot, không phải trường W12.
- Chỉ dùng offering ACTIVE. Giá là giá món niêm yết theo W12, không tự thêm phí, ưu đãi, topping, thành phần, cam kết giao hàng, tồn kho hay chính sách.
- Đây là **tình huống giả lập** cho dữ liệu hội thoại. Lời nhân viên như “em ghi món” không chứng minh đơn hàng đã tồn tại trong W12; không tự sinh order hay xác nhận thanh toán/giao hàng thực tế.
- Gợi ý combo có điều kiện: W12 có tên và giá combo, nhưng **chưa xác minh** danh sách món áp dụng. Không khẳng định một loại cà phê/trà cụ thể được đổi sang combo nếu chưa có bằng chứng.
- Không gán “bán chạy”, “đang trend”, “best seller” khi không có số liệu bán hàng xác minh. Giữ ngôn ngữ tự nhiên, đúng mối quan tâm, không upsell trong lúc khách khiếu nại.
- Nội dung KH/NV đã biên tập thủ công; Codex **không tự viết lại**. Nếu có xung đột W12 hoặc lỗi tính tiền, báo cụ thể để người dùng duyệt thay vì âm thầm sửa.

## Hội thoại

### RET-001

- **Entity:** `Customer` · **UUID:** `00d4d96e-62a3-5b36-95a2-38374094e8ae` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CAC-KEM-BMT` · **Split:** `dev`

**NV**: Dạ em chào chị, hôm nay chị muốn dùng gì ạ?
**KH**: Cho chị một cacao kem BMT nha, với coi có bánh nào ăn kèm không em.
**NV**: Dạ cacao kem BMT 32 nghìn một ly. Nếu chị muốn ăn nhẹ, bên em có bánh croissant bơ Pháp 35 nghìn một cái ạ.
**KH**: Thôi chị lấy mỗi cacao, đang vội.
**NV**: Dạ được chị. Em ghi một cacao kem BMT 32 nghìn, không thêm bánh nha.
**KH**: Ừ đúng rồi, cảm ơn em.
**NV**: Dạ em cảm ơn chị, chúc chị một ngày vui ạ.

### RET-002

- **Entity:** `Customer` · **UUID:** `0121b16f-7154-5fde-84bd-3c2de6296bab` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CF-COLDBREWMILK` · **Split:** `dev`

**KH**: Shop ơi cold brew milk nhiêu tiền vậy?
**NV**: Dạ Cold Brew Milk 39 nghìn một ly nha anh. Anh muốn đặt một ly hay đang tham khảo ạ?
**KH**: Anh lấy một ly. Mà có bánh mì nướng bơ tỏi không?
**NV**: Dạ món bánh mì nướng bơ tỏi có trong menu, giá 29 nghìn một cái. Anh muốn thêm vào cùng đồ uống không ạ?
**KH**: Có, thêm một cái. Tổng nhiêu em?
**NV**: Dạ tổng giá hai món là 68 nghìn ạ. Em nhắc lại: một Cold Brew Milk, một bánh mì nướng bơ tỏi.
**KH**: Chuẩn rồi em.

### RET-003

- **Entity:** `Customer` · **UUID:** `1713e166-7349-5a25-abbf-62aea8c9f240` · **Status:** `ACTIVE`
- **Offering W12:** `COMBO_LUNCH` · **Split:** `dev`

**KH**: Em ơi, trưa nay chị cần đặt cơm cho mấy bạn trong phòng.
**NV**: Dạ em chào chị. Bên em có combo cơm trưa văn phòng 55 nghìn một phần. Chị dự tính mấy phần ạ?
**KH**: Sáu phần. Có chọn món trong combo được không?
**NV**: Dạ phần món trong combo em cần kiểm tra lại. Em ghi nhu cầu sáu phần trước nhé chị.
**KH**: Ừ, tại có một bạn không ăn cay.
**NV**: Dạ em ghi chú yêu cầu đó để kiểm tra cùng thực đơn; em sẽ hỏi kỹ phần món và độ cay để chị cân nhắc nha.
**KH**: Có thông tin thì báo chị, chưa cần chốt.

### RET-004

- **Entity:** `Customer` · **UUID:** `1b30154a-6caf-5235-99e8-8c8c87453e95` · **Status:** `ACTIVE`
- **Offering W12:** `RET-MTE-GAO-RANG` · **Split:** `dev`

**NV**: Dạ em chào anh, anh dùng gì ạ?
**KH**: Anh muốn trà sữa gạo rang. Ly đó bao nhiêu?
**NV**: Dạ 20 nghìn một ly anh nha. Anh muốn lấy một ly trước hay thêm món nào khác ạ?
**KH**: Một ly thôi. À có combo sinh viên không?
**NV**: Dạ có combo sinh viên gồm một trà và một bánh mì, giá 45 nghìn. Để em xem trà anh chọn có nằm trong combo không nha.
**KH**: Vậy thôi cho anh ly gạo rang lẻ, khỏi mất công.
**NV**: Dạ em ghi một trà sữa gạo rang 20 nghìn, không thêm món ạ.

### RET-005

- **Entity:** `Customer` · **UUID:** `249537ec-4d4e-5e85-8b49-1f0d3208440f` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `dev`

**KH**: Chị ơi em đi học, muốn mua gì vừa uống vừa ăn mà đỡ tốn.
**NV**: Chị chào em. Bên chị có combo sinh viên một trà với một bánh mì, giá 45 nghìn/combo nè.
**KH**: Nghe được á. Vậy em lấy combo đó nha.
**NV**: Ừ, chị ghi nhận một combo sinh viên 45 nghìn. Chị xem lại món trà và bánh mì áp dụng trong combo rồi chốt cho em nha.
**KH**: Dạ, chị hỏi giúp em, em muốn trà sữa gạo rang.
**NV**: Chị ghi mong muốn của em rồi. Nếu món đó không thuộc combo chị báo để em chọn, không tự đổi món đâu.

### RET-006

- **Entity:** `Customer` · **UUID:** `25b455b0-f40e-57a4-83e2-ca3f61374aec` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CF-COLDBREWMILK` · **Split:** `dev`

**KH**: Ê shop, mình đang họp mà thèm cold brew quá.
**NV**: Dạ mình chào bạn nha. Cold Brew Milk bên mình 39k/ly, bạn muốn mấy ly nè?
**KH**: Hai ly. Có combo cho cả phòng không?
**NV**: Bên mình có combo văn phòng 5 ly giá 150k, còn hai ly Cold Brew Milk lẻ là 78k. Bạn xem nhu cầu nhóm mình thế nào nha.
**KH**: Phòng có đúng hai người thôi, lấy hai ly lẻ.
**NV**: Oki bạn, mình ghi hai Cold Brew Milk, tổng giá món 78k nhé.
**KH**: Đúng rùi, cảm ơn shop.

### RET-007

- **Entity:** `Customer` · **UUID:** `26d5232d-4561-5cda-b004-a71bdc023395` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `dev`

**NV**: Dạ chị chào em, em muốn dùng gì hôm nay?
**KH**: Em định lấy combo sinh viên mà bạn em không thích bánh mì.
**NV**: Dạ combo sinh viên bên chị là một trà với một bánh mì, 45k. Nếu không dùng bánh mì thì em có thể chọn đồ uống lẻ cho hợp nhu cầu nha.
**KH**: Vậy cho em một trà dâu tây đi chị, còn bạn em lấy trà gạo rang.
**NV**: Dạ trà dâu tây 32k, trà sữa gạo rang 20k. Hai ly tổng 52k, chị ghi vậy nha?
**KH**: Dạ đúng rồi chị, khỏi bánh.
**NV**: Ừ, chị ghi hai ly lẻ, không có bánh nhé.

### RET-008

- **Entity:** `Customer` · **UUID:** `38ddf9eb-f919-5d78-8a95-b8d504a4e296` · **Status:** `ACTIVE`
- **Offering W12:** `MILKTEA_S` · **Split:** `dev`

**KH**: Alo shop, trà sữa trân châu size L 39k đúng không?
**NV**: Dạ đúng rồi chị, trà sữa trân châu size L giá 39 nghìn một ly ạ.
**KH**: Cho chị ba ly. Một ly ít ngọt được không?
**NV**: Dạ em ghi chú chị muốn một ly ít ngọt. Mức tùy chỉnh cụ thể em sẽ xác nhận với bộ phận pha chế, chưa dám hứa trước ạ.
**KH**: Oke. Tính tiền ba ly đi.
**NV**: Dạ giá ba ly là 117 nghìn. Em nhắc lại ba trà sữa trân châu size L, một ly có yêu cầu ít ngọt nhé.
**KH**: Ừ đúng rồi.

### RET-009

- **Entity:** `Customer` · **UUID:** `3de366ea-5f5e-53fa-8c7f-1e6d6d6d9f27` · **Status:** `ACTIVE`
- **Offering W12:** `RET-FRT-DUA-LUOI` · **Split:** `dev`

**KH**: Trà dưa lưới bên mình có không em?
**NV**: Dạ có trong menu chị nha, trà dưa lưới 30 nghìn một ly.
**KH**: Chị muốn mua cho mẹ, có chắc không bị dị ứng gì không?
**NV**: Dạ về thành phần và dị ứng em không dám khẳng định khi chưa có bảng nguyên liệu. Chị cho em biết mẹ cần tránh thành phần nào để em hỏi lại cho chính xác nhé.
**KH**: Mẹ chị tránh sữa.
**NV**: Dạ em ghi rõ yêu cầu tránh sữa và kiểm tra thành phần trước khi chị đặt, không tự đoán món này có hay không có sữa ạ.
**KH**: Ừ vậy em kiểm tra rồi chị quyết định.

### RET-010

- **Entity:** `Customer` · **UUID:** `47056d11-6e46-55f4-a9bd-9467b2510c38` · **Status:** `ACTIVE`
- **Offering W12:** `RET-MTE-GAO-RANG` · **Split:** `dev`

**NV**: Dạ em chào chị, chị dùng gì nè?
**KH**: Chị lấy trà sữa gạo rang. Có gì ăn kèm không?
**NV**: Dạ trà sữa gạo rang 20k/ly. Nếu chị muốn thêm bánh, có bánh mì nướng bơ tỏi 29k hoặc croissant 35k ạ.
**KH**: Croissant đi em. À thôi, chị đổi sang bánh mì bơ tỏi.
**NV**: Dạ em sửa lại thành một trà sữa gạo rang và một bánh mì nướng bơ tỏi. Tổng 49k, đúng ý chị chưa ạ?
**KH**: Đúng rồi, không thêm gì nữa.
**NV**: Dạ em cảm ơn chị, em giữ đúng hai món này nha.

### RET-011

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004327` · **Status:** `ACTIVE`
- **Offering W12:** `RET-FRT-DUA-LUOI` · **Split:** `dev`

**KH**: Cho mình trà dưa lưới, nhanh nhanh xíu được không?
**NV**: Dạ mình chào bạn. Trà dưa lưới 30k một ly nha. Bạn đang cần gấp đúng không?
**KH**: Ừ mình còn mười phút nữa vào lớp.
**NV**: Mình hiểu bạn đang gấp. Để mình hỏi quầy xem có kịp trong mười phút không rồi báo bạn ngay nhé.
**KH**: Vậy kiểm tra giùm mình, không kịp thì thôi.
**NV**: Dạ được, mình hỏi ngay tình trạng phục vụ rồi báo bạn quyết định, chưa tự xác nhận đơn nhé.

### RET-012

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004329` · **Status:** `ACTIVE`
- **Offering W12:** `RET-FOOD-SANDWICH` · **Split:** `dev`

**NV**: Dạ em chào anh, anh chọn món gì ạ?
**KH**: Một sandwich thịt nguội với cà phê sữa đá nha.
**NV**: Dạ sandwich thịt nguội 55k một cái. Cà phê sữa đá Sài Gòn 25k một ly, anh muốn loại đó đúng không ạ?
**KH**: Ừ đúng rồi. Có combo nào rẻ hơn không?
**NV**: Bên em có combo sáng gồm cà phê và croissant 55k, nhưng không phải sandwich. Nếu anh muốn giữ sandwich thì hai món lẻ đang là 80k ạ.
**KH**: Anh thích sandwich hơn, cứ hai món đó.
**NV**: Dạ em ghi đúng sandwich với cà phê sữa đá, tổng 80k anh nhé.

### RET-013

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004330` · **Status:** `ACTIVE`
- **Offering W12:** `RET-FRT-DUA-LUOI` · **Split:** `dev`

**KH**: Shop ơi mình đặt trà dưa lưới rồi mà trên giấy lại ghi trà đào.
**NV**: Dạ mình xin lỗi bạn vì thông tin món đang không khớp. Bạn cho mình xem phần order hoặc hóa đơn để mình đối chiếu ngay nha.
**KH**: Đây, mình nhắn dưa lưới rõ mà.
**NV**: Dạ mình thấy bạn đã ghi trà dưa lưới. Mình kiểm tra lại khâu chuẩn bị món rồi báo bạn cách xử lý nha, bạn đợi mình một chút nhé.
**KH**: Mình đang đứng chờ đó.
**NV**: Dạ mình biết bạn đang chờ, mình ưu tiên đối chiếu ngay. Mình chưa tự kết luận lỗi ở khâu nào khi chưa kiểm tra xong.

### RET-014

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004331` · **Status:** `ACTIVE`
- **Offering W12:** `MILKTEA_S` · **Split:** `dev`

**KH**: Em ơi chị đặt trà sữa size L mà hình như hóa đơn tính hai ly?
**NV**: Dạ em xin lỗi chị vì khoản tiền đang khiến chị băn khoăn. Chị gửi em hóa đơn để em đối chiếu số lượng với đơn nha.
**KH**: Đây, chị chỉ gọi một ly thôi.
**NV**: Dạ em thấy chị báo một ly trà sữa trân châu size L giá menu 39k. Em sẽ kiểm tra dòng số lượng trên hóa đơn và giao dịch trước khi kết luận ạ.
**KH**: Chị cần biết có bị trừ dư không.
**NV**: Dạ em hiểu. Em sẽ báo rõ kết quả đối chiếu và phương án xử lý theo chính sách được xác minh, không để chị phải đoán ạ.

### RET-015

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004332` · **Status:** `ACTIVE`
- **Offering W12:** `RET-FOOD-SANDWICH` · **Split:** `dev`

**NV**: Dạ chị chào em, em dùng gì nè?
**KH**: Cho em sandwich thịt nguội. Mà chị ơi có món nào rẻ hơn không?
**NV**: Sandwich 55k/cái. Nếu em muốn ăn nhẹ hơn thì bánh mì nướng bơ tỏi 29k/cái hoặc croissant 35k/cái nha.
**KH**: Vậy em đổi sang croissant, thêm một cà phê sữa phin.
**NV**: Dạ croissant 35k và cà phê sữa phin máy 19k, tổng 54k. Chị đổi lại đúng hai món này cho em nha?
**KH**: Dạ đúng, em lấy vậy.
**NV**: Oke em, chị không giữ sandwich trong order nữa nhé.

### RET-016

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004335` · **Status:** `ACTIVE`
- **Offering W12:** `RET-FOOD-SANDWICH` · **Split:** `dev`

**KH**: Chị ơi em đặt sandwich mà muốn bỏ một thành phần bên trong được không?
**NV**: Dạ chị chào em. Em cần tránh thành phần nào vậy, để chị hỏi lại bên chuẩn bị món nha?
**KH**: Em dị ứng với một loại sốt, em không biết sandwich có không.
**NV**: Vậy mình chưa chốt món vội em nhé. Chị sẽ kiểm tra thông tin nguyên liệu và khả năng điều chỉnh, vì chị không muốn đoán sai chuyện dị ứng.
**KH**: Dạ, nếu không chắc thì em chọn bánh khác.
**NV**: Ừ em, chị ghi rõ yêu cầu an toàn thực phẩm của em và chỉ tư vấn tiếp khi có thông tin được xác nhận nha.

### RET-017

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004337` · **Status:** `ACTIVE`
- **Offering W12:** `MILKTEA_S` · **Split:** `dev`

**NV**: Dạ em chào anh, anh dùng gì ạ?
**KH**: Anh lấy hai trà sữa trân châu size L, một bánh croissant.
**NV**: Dạ hai trà sữa size L là 78k, croissant 35k. Anh có muốn thêm món nào nữa không ạ?
**KH**: Không em, vậy đủ rồi.
**NV**: Dạ tổng giá món là 113k. Em nhắc lại hai trà sữa trân châu size L và một croissant, anh kiểm tra giúp em nha.
**KH**: Chuẩn rồi.
**NV**: Dạ em cảm ơn anh ạ.

### RET-018

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004338` · **Status:** `ACTIVE`
- **Offering W12:** `RET-FOOD-SANDWICH` · **Split:** `dev`

**KH**: Bên em có sandwich thịt nguội không? Anh cần đặt cho cuộc họp.
**NV**: Dạ có trên menu anh nha, 55k một cái. Anh cần khoảng bao nhiêu phần để em ghi nhu cầu ạ?
**KH**: Tám cái. Có giao tới công ty anh được không?
**NV**: Dạ em ghi tám sandwich, tổng giá món 440k. Phạm vi giao và phí giao em cần xác minh theo địa chỉ trước khi nhận lời anh nhé.
**KH**: Công ty anh ở quận khác, để anh gửi địa chỉ.
**NV**: Dạ anh gửi địa chỉ giúp em, em kiểm tra khả năng phục vụ rồi báo rõ chi phí nếu có trước khi mình xác nhận.

### RET-019

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004341` · **Status:** `ACTIVE`
- **Offering W12:** `MILKTEA_S` · **Split:** `dev`

**KH**: Trà sữa size L của chị sao nhìn khác hình chị thấy trên mạng vậy em?
**NV**: Dạ em chào chị. Chị gửi em ảnh ly nhận được và hình chị tham khảo để em kiểm tra đúng món nha.
**KH**: Đây, chị tưởng có topping khác cơ.
**NV**: Dạ em hiểu rồi chị. Trà sữa trân châu size L là 39k nha chị. Còn phần topping thì em hỏi lại rồi báo chị nhé.
**KH**: Vậy em xem giúp chị có bị thiếu không.
**NV**: Dạ em đối chiếu hình với thông tin chị đã đặt rồi báo chị cụ thể nhé.

### RET-020

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004343` · **Status:** `ACTIVE`
- **Offering W12:** `MILKTEA_S` · **Split:** `dev`

**NV**: Dạ chị chào em, em uống gì nè?
**KH**: Cho em một trà sữa trân châu size L. Em có thể mua thêm bánh gì?
**NV**: Dạ trà sữa 39k/ly. Bánh croissant 35k, bánh mì nướng bơ tỏi 29k; em thích bánh ngọt hay bánh mặn để chị gợi ý?
**KH**: Em thích bánh mặn, lấy bánh mì bơ tỏi nha.
**NV**: Dạ một trà sữa size L với một bánh mì nướng bơ tỏi, tổng 68k. Em còn muốn thêm gì không?
**KH**: Dạ thôi chị, đủ rồi.
**NV**: Ừ, chị ghi hai món thôi nha em.

### RET-021

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004347` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CF-PHIN-DEN` · **Split:** `dev`

**KH**: Em ơi cà phê đen phin máy bao nhiêu?
**NV**: Dạ 17 nghìn một ly anh nha. Anh dùng một ly ạ?
**KH**: Ừ. Có món gì ăn sáng gọn gọn không?
**NV**: Dạ bên em có croissant bơ Pháp 35k/cái. Ngoài ra combo sáng một cà phê với một croissant là 55k, nhưng với ly cà phê đen 17k thì mua lẻ hai món chỉ 52k thôi ạ.
**KH**: Vậy cho anh mua lẻ, em tư vấn vậy hợp lý đó.
**NV**: Dạ em ghi một cà phê đen phin máy và một croissant, tổng 52k, không chuyển combo ạ.

### RET-022

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004349` · **Status:** `ACTIVE`
- **Offering W12:** `MILKTEA_S` · **Split:** `dev`

**NV**: Dạ em chào chị, chị dùng gì hôm nay ạ?
**KH**: Chị mua bốn trà sữa trân châu size L cho mấy bạn.
**NV**: Dạ bốn ly là 156k. Nếu nhóm chị muốn thêm bánh dùng chung thì bên em có croissant 35k/cái ạ.
**KH**: Không em, mấy bạn chỉ uống thôi.
**NV**: Dạ em ghi bốn ly trà sữa size L, tổng giá món 156k. Chị có yêu cầu nào cho từng ly không ạ?
**KH**: Không, giống nhau hết.
**NV**: Dạ vậy em giữ bốn ly cùng loại, cảm ơn chị nha.

### RET-023

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004353` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CF-PHIN-SUA` · **Split:** `dev`

**KH**: Chị ơi, cho em cà phê sữa phin máy.
**NV**: Dạ chị chào em, cà phê sữa phin máy 19k một ly nha.
**KH**: Em lấy hai ly. Mà em lỡ nhắn nhầm, chỉ một ly thôi chị.
**NV**: Ừ không sao em, chị sửa còn một ly, không tính hai ly nhé.
**KH**: Dạ, em cảm ơn chị. Có bánh nào dưới 30k không?
**NV**: Có bánh mì nướng bơ tỏi 29k/cái. Em muốn thêm không?
**KH**: Dạ thêm một cái, tổng 48k đúng không chị?
**NV**: Đúng rồi em, một cà phê sữa phin và một bánh mì bơ tỏi là 48k.

### RET-024

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004355` · **Status:** `ACTIVE`
- **Offering W12:** `RET-FOOD-PAIN-FRAISE` · **Split:** `dev`

**KH**: Shop ơi bánh dâu cuộn Pain Aux Fraise còn không?
**NV**: Dạ món Pain Aux Fraise có trên menu giá 39k/cái, còn số lượng hiện tại mình cần kiểm tra tại cửa hàng nha bạn.
**KH**: Mình muốn mua hai cái tặng bạn, có gói quà được không?
**NV**: Mình ghi nhu cầu hai bánh, giá món 78k. Dịch vụ gói quà mình chưa có thông tin xác nhận, để mình hỏi rồi báo trước khi bạn quyết định nhé.
**KH**: Oki, đừng tự thêm tiền gói quà nha.
**NV**: Dạ hai bánh là 78k nha bạn. Còn phần gói quà, mình xem cửa hàng có hỗ trợ không rồi báo bạn nhé.

### RET-025

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004358` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `dev`

**NV**: Dạ chị chào em, em muốn dùng gì nè?
**KH**: Em đi cùng bạn, tụi em muốn combo sinh viên 45k.
**NV**: Dạ combo sinh viên gồm một trà và một bánh mì, 45k/combo. Hai bạn muốn mỗi người một combo phải không?
**KH**: Dạ đúng, nhưng bạn em không ăn bánh mì, đổi thành croissant được không?
**NV**: Chị chưa có quy định đổi bánh trong combo nên không hứa đổi ngang được. Chị sẽ kiểm tra lựa chọn áp dụng rồi hai em quyết định nhé.
**KH**: Nếu không đổi được thì bạn em mua ly lẻ.
**NV**: Ừ chị ghi nhận phương án đó, chưa tự đổi combo cho bạn em nha.

### RET-026

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004359` · **Status:** `ACTIVE`
- **Offering W12:** `MILKTEA_S` · **Split:** `dev`

**KH**: Em ơi chị vừa order hai ly trà sữa, giờ muốn đổi một ly sang trà đào cam sả.
**NV**: Dạ được chị, để em đối chiếu tình trạng chuẩn bị món trước khi sửa nha. Trà đào cam sả giá 30k/ly, trà sữa size L 39k/ly.
**KH**: Chị mới nhắn lúc nãy thôi, chưa thấy ai làm.
**NV**: Dạ em ghi yêu cầu đổi một ly, nhưng vẫn cần xác nhận đơn đã vào khâu pha chế chưa. Nếu đổi được thì tổng giá hai ly sẽ là 69k ạ.
**KH**: Ừ, em kiểm tra giúp chị rồi hãy sửa.
**NV**: Dạ em sẽ báo rõ kết quả, không tự xác nhận đã đổi khi chưa đối chiếu nhé.

### RET-027

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004361` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `dev`

**NV**: Dạ chị chào em, em cần tư vấn món gì không?
**KH**: Em muốn combo sinh viên nhưng chỉ có 40 nghìn thôi chị.
**NV**: Combo sinh viên bên chị 45k, hơi vượt ngân sách của em. Nếu em chỉ cần đồ uống thì trà sữa gạo rang 20k/ly nha.
**KH**: Vậy cho em trà gạo rang với bánh mì bơ tỏi thì sao?
**NV**: Hai món đó là 20k cộng 29k, tổng 49k em ạ. Nếu muốn giữ dưới 40k thì em lấy một ly trà trước nhé.
**KH**: Dạ em lấy ly trà thôi.
**NV**: Ừ chị ghi một trà sữa gạo rang 20k nha em.

### RET-028

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004364` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `dev`

**KH**: Chị ơi combo sinh viên 45k có bắt buộc phải có thẻ sinh viên không?
**NV**: Dạ giá combo sinh viên trong menu là 45k, nhưng điều kiện áp dụng cụ thể chị cần xác minh trước khi nói chắc với em nha.
**KH**: Tại em quên thẻ ở nhà, sợ ra quầy không mua được.
**NV**: Chị hiểu, để chị kiểm tra điều kiện rồi báo rõ cho em trước khi em đến, tránh em mất công.
**KH**: Nếu không được thì em mua trà sữa gạo rang lẻ.
**NV**: Dạ trà sữa gạo rang 20k/ly. Chị ghi phương án dự phòng đó, chưa tự xác nhận combo nhé.

### RET-029

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004365` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `dev`

**NV**: Dạ chị chào em, em chọn món gì hôm nay?
**KH**: Dạ em lấy combo sinh viên. Mà em muốn thêm bánh nữa.
**NV**: Combo sinh viên 45k gồm một trà và một bánh mì. Nếu em muốn thêm croissant thì bánh đó 35k/cái nha.
**KH**: Thôi mắc quá, em chỉ lấy combo thôi.
**NV**: Ừ, chị không thêm croissant nhé. Em muốn chọn trà nào để chị kiểm tra danh sách áp dụng trong combo?
**KH**: Em thích trà sữa gạo rang.
**NV**: Chị ghi mong muốn đó rồi, xác nhận được loại trà chị mới hoàn tất lựa chọn cho em nha.

### RET-030

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004366` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CF-COLDBREWMILK` · **Split:** `dev`

**KH**: Shop ơi hôm trước mình uống Cold Brew Milk, giờ muốn mua lại.
**NV**: Dạ vui quá, bạn quay lại ủng hộ nè. Cold Brew Milk vẫn nằm trong menu giá 39k/ly nha.
**KH**: Mình lấy một ly, thêm bánh gì dễ ăn?
**NV**: Bạn có thể cân nhắc croissant bơ Pháp 35k hoặc bánh mì nướng bơ tỏi 29k. Bạn thích vị bánh kiểu nào?
**KH**: Bánh mì bơ tỏi đi, không cần thêm gì khác.
**NV**: Dạ một Cold Brew Milk và một bánh mì nướng bơ tỏi, tổng giá món 68k. Cảm ơn bạn quay lại nha.

### RET-031

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004367` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `dev`

**KH**: Chị ơi em đặt combo sinh viên rồi mà bên mình nói chưa rõ trà nào được chọn.
**NV**: Dạ chị xin lỗi vì phần tư vấn chưa rõ làm em phải hỏi lại. Chị kiểm tra danh sách trà áp dụng trong combo 45k cho em nha.
**KH**: Em hỏi hai lần rồi đó chị.
**NV**: Ừ chị hiểu em khó chịu vì phải hỏi lại. Chị sẽ hỏi đúng danh sách trà áp dụng để trả lời em cho rõ nha.
**KH**: Em chỉ cần biết có trà gạo rang không.
**NV**: Dạ chị tập trung kiểm tra đúng món trà gạo rang cho em trước, chưa tự chốt đơn khi chưa rõ điều kiện nhé.

### RET-032

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004369` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CF-COLDBREWMILK` · **Split:** `dev`

**NV**: Dạ em chào anh, anh muốn uống gì ạ?
**KH**: Cold Brew Milk. Mà anh không muốn cà phê ngọt quá.
**NV**: Dạ Cold Brew Milk 39k/ly. Anh muốn điều chỉnh độ ngọt phải không ạ? Em sẽ kiểm tra khả năng điều chỉnh với bên pha chế trước khi nhận lời.
**KH**: Ừ, nếu không chỉnh được thì anh chọn món khác.
**NV**: Dạ em hiểu. Em có thể giới thiệu cà phê đen phin máy 17k/ly để anh cân nhắc, nhưng mức ngọt và thành phần cụ thể em không tự khẳng định nha.
**KH**: Vậy để anh chờ thông tin Cold Brew đã.
**NV**: Dạ được anh, em kiểm tra rồi anh chọn sau nhé.

### RET-033

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004370` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `dev`

**KH**: Em ơi chị mua combo sinh viên mà sao tổng tiền lại khác 45k?
**NV**: Dạ em xin lỗi chị vì phần giá chưa rõ. Chị cho em xem hóa đơn hoặc các món đã chọn để em đối chiếu nhé.
**KH**: Chị thấy có thêm một dòng tiền mà không hiểu.
**NV**: Dạ combo trên menu là 45k. Em kiểm tra khoản phát sinh đó là gì rồi báo rõ cho chị nhé.
**KH**: Ừ, chị không đồng ý trả thêm khoản chưa được báo.
**NV**: Dạ em ghi nhận rõ ý chị. Em sẽ đối chiếu rồi giải thích từng khoản trước khi mình quyết định xử lý tiếp ạ.

### RET-034

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004371` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CF-COLDBREWMILK` · **Split:** `dev`

**NV**: Dạ chị chào em, em dùng gì nè?
**KH**: Em lấy Cold Brew Milk với bánh croissant. À mà có combo sáng không chị?
**NV**: Có combo sáng một cà phê và một croissant giá 55k. Nhưng loại cà phê được áp dụng cần đối chiếu trước khi chị đổi cho em nhé.
**KH**: Nếu Cold Brew không được thì em cứ mua lẻ.
**NV**: Dạ mua lẻ Cold Brew Milk 39k với croissant 35k là 74k. Chị sẽ kiểm tra combo trước, nếu không phù hợp chị giữ hai món lẻ theo ý em.
**KH**: Oke chị, đừng tự thay cà phê của em nha.
**NV**: Ừ, chị giữ đúng Cold Brew Milk em chọn, chỉ đổi khi em đồng ý nhé.

### RET-035

- **Entity:** `Customer` · **UUID:** `50000000-0000-5000-8000-000000004372` · **Status:** `ACTIVE`
- **Offering W12:** `RET-CF-COLDBREWMILK` · **Split:** `dev`

**KH**: Shop ơi đặt năm Cold Brew Milk cho phòng được không?
**NV**: Dạ được mình ghi nhu cầu năm ly cho bạn. Cold Brew Milk 39k/ly, năm ly lẻ là 195k.
**KH**: Có combo văn phòng năm ly 150k mà?
**NV**: Dạ đúng, menu có combo văn phòng năm ly giá 150k. Để mình xem Cold Brew Milk có áp dụng trong combo văn phòng không rồi tư vấn bạn cách chọn hợp lý nha.
**KH**: Nếu không được thì bạn báo mình, đừng tự đổi sang trà.
**NV**: Dạ chắc chắn rồi, mình giữ nguyên yêu cầu năm Cold Brew Milk và báo rõ lựa chọn trước khi bạn chốt.

### RET-036

- **Entity:** `Lead` · **UUID:** `00023d87-d36d-415e-8a89-f673e3679add` · **Status:** `NEW`
- **Offering W12:** `RET-FOOD-TOAST` · **Split:** `dev`

**NV**: Dạ em chào chị, chị muốn dùng gì ạ?
**KH**: Cho chị một bánh mì nướng bơ tỏi, bao nhiêu tiền em?
**NV**: Dạ 29 nghìn một cái chị nha. Chị có muốn thêm cà phê sữa phin 19 nghìn để dùng cùng không ạ?
**KH**: Chị đang uống nước rồi, khỏi em.
**NV**: Dạ em ghi một bánh mì nướng bơ tỏi 29 nghìn, không thêm đồ uống nhé.
**KH**: Ừ cảm ơn em.
**NV**: Dạ em cảm ơn chị đã ghé ạ.

### RET-037

- **Entity:** `Lead` · **UUID:** `001807d1-4671-9350-be3f-fecdd8b9e0f1` · **Status:** `NEW`
- **Offering W12:** `RET-CAC-KEM-TRUNG` · **Split:** `dev`

**KH**: Bên mình có cacao kem trứng không? Mình thấy tên nghe lạ.
**NV**: Dạ có bạn nha, cacao kem trứng 32k/ly. Bạn đang muốn thử món cacao hay tìm đồ uống khác cà phê?
**KH**: Mình muốn cacao, nhưng không rõ có trứng thật không.
**NV**: Tên món là cacao kem trứng, còn thành phần cụ thể mình cần xác nhận từ thông tin nguyên liệu, không đoán chỉ dựa vào tên nha.
**KH**: Mình dị ứng trứng nên phải hỏi kỹ.
**NV**: Vậy mình ưu tiên kiểm tra thành phần và nguy cơ dị ứng trước khi bạn đặt. Bạn chưa cần quyết định vội nhé.

### RET-038

- **Entity:** `Lead` · **UUID:** `00258456-8d54-4e51-8468-3c5092de1950` · **Status:** `QUALIFIED`
- **Offering W12:** `RET-MTE-GAO-RANG` · **Split:** `dev`

**NV**: Dạ em chào anh, anh đang tìm món gì ạ?
**KH**: Trà sữa gạo rang 20k đúng không? Anh muốn thử.
**NV**: Dạ đúng anh, 20k/ly. Anh thích uống riêng hay muốn thêm bánh ăn cùng ạ?
**KH**: Có bánh mì bơ tỏi không?
**NV**: Dạ có, 29k/cái. Một trà gạo rang với một bánh mì bơ tỏi là 49k anh nha.
**KH**: Vậy lấy cả hai, khỏi thêm gì.
**NV**: Dạ em nhắc lại hai món, tổng 49k. Cảm ơn anh ạ.

### RET-039

- **Entity:** `Lead` · **UUID:** `002a9b99-ccc6-5358-b650-e7b6a535101e` · **Status:** `NEW`
- **Offering W12:** `RET-CF-SUA-DA-SG` · **Split:** `dev`

**KH**: Em ơi cà phê sữa đá Sài Gòn có trên menu không?
**NV**: Dạ có chị nha, 25k/ly. Chị muốn một ly ạ?
**KH**: Chị đặt hai ly, một ly cho đồng nghiệp. Có thể ghi chú phân biệt giúp chị không?
**NV**: Dạ em có thể ghi chú hai ly thuộc hai người khác nhau; còn cách dán nhãn cụ thể em sẽ xác nhận tại quầy trước khi hứa nhé.
**KH**: Vậy ghi một ly của Lan, một ly của Mai.
**NV**: Dạ em ghi chú Lan và Mai, hai cà phê sữa đá Sài Gòn tổng 50k. Chị xem đúng chưa ạ?
**KH**: Đúng em.

### RET-040

- **Entity:** `Lead` · **UUID:** `002ba4b0-2b32-8958-8cf3-3ab583b4459c` · **Status:** `QUALIFIED`
- **Offering W12:** `RET-FRT-MANG-CAU` · **Split:** `dev`

**KH**: Shop ơi trà mãng cầu giá nhiêu?
**NV**: Dạ trà mãng cầu 30k/ly bạn nha.
**KH**: Mình định mua cho nhóm bốn người, mà có bạn thích trà dâu.
**NV**: Dạ trà dâu tây 32k/ly. Bạn muốn chia số lượng thế nào để mình tính rõ từng món?
**KH**: Ba mãng cầu, một trà dâu tây.
**NV**: Dạ ba trà mãng cầu 90k và một trà dâu tây 32k, tổng 122k. Bạn có muốn thêm món gì không?
**KH**: Không nha, vậy đủ rồi.

### RET-041

- **Entity:** `Lead` · **UUID:** `004b2a58-8848-ad5b-a548-9710e3eae934` · **Status:** `NEW`
- **Offering W12:** `COMBO_FAMILY` · **Split:** `test`

**KH**: Em ơi nhà chị có bốn người, bên mình có combo gia đình không?
**NV**: Dạ em chào chị, bên em có combo gia đình bốn người giá 389k một phần ạ.
**KH**: Trong đó có những món nào vậy? Chị định đặt cho tối nay.
**NV**: Dạ em chưa có danh sách món chi tiết và thời gian phục vụ được xác minh, nên em sẽ kiểm tra trước khi chị quyết định nha.
**KH**: Ừ, có người lớn tuổi nên chị cần biết món gì.
**NV**: Dạ em ghi nhận chị cần phần ăn cho bốn người. Em kiểm tra danh sách món trong combo rồi báo chị chính xác nhé.

### RET-042

- **Entity:** `Lead` · **UUID:** `00524c42-84f7-c95f-9030-ab5fc3c2aa4f` · **Status:** `NEW`
- **Offering W12:** `RET-MAT-LATTE` · **Split:** `test`

**NV**: Dạ mình chào bạn, bạn muốn dùng gì nè?
**KH**: Mình đang phân vân matcha latte với houjicha latte.
**NV**: Dạ hai món đều có trong menu, mỗi ly 27k. Bạn đang thích thử món nào hơn?
**KH**: Matcha latte đi. Có thể thêm bánh không?
**NV**: Có bánh croissant 35k/cái, nếu bạn muốn dùng kèm nha.
**KH**: Thôi mình thử matcha trước, bánh để lần sau.
**NV**: Oki, một matcha latte 27k, không thêm bánh nhé. Cảm ơn bạn!

### RET-043

- **Entity:** `Lead` · **UUID:** `007969a1-1563-8250-b597-61c241364c0a` · **Status:** `NEW`
- **Offering W12:** `RET-FOOD-PAIN-FRAISE` · **Split:** `test`

**KH**: Chị ơi bánh dâu cuộn có đẹp để tặng sinh nhật không?
**NV**: Dạ chị chào em. Pain Aux Fraise bánh dâu cuộn có trên menu giá 39k/cái. Về hình thức thực tế và đóng gói, chị có thể kiểm tra hình sản phẩm hiện tại trước khi tư vấn chắc cho em.
**KH**: Em muốn mua ba cái, có nến không chị?
**NV**: Ba bánh giá món 117k. Còn nến hoặc phụ kiện sinh nhật, để chị hỏi cửa hàng có hỗ trợ không nha.
**KH**: Dạ, nếu không có nến em vẫn lấy bánh.
**NV**: Ừ chị ghi mong muốn ba bánh và câu hỏi về nến, chờ xác nhận tình trạng bánh rồi mình chốt em nha.

### RET-044

- **Entity:** `Lead` · **UUID:** `007e2ab1-b215-9059-a862-8d574c93fbad` · **Status:** `NEW`
- **Offering W12:** `RET-MTE-TC-KEMTRUNG` · **Split:** `test`

**NV**: Dạ em chào chị, chị muốn dùng món nào ạ?
**KH**: Sữa tươi trân châu kem trứng bao nhiêu em?
**NV**: Dạ 33k một ly chị nha. Chị muốn lấy một ly ạ?
**KH**: Ừ. Nhưng chị không uống được trứng, món đó có trứng không?
**NV**: Dạ vì chị cần tránh trứng, để em hỏi lại thành phần với bộ phận pha chế trước khi chị chọn món nha.
**KH**: Vậy cho chị xem món khác sau khi em kiểm tra.
**NV**: Dạ được chị, em ưu tiên xác minh thành phần trước nhé.

### RET-045

- **Entity:** `Lead` · **UUID:** `0099e8b6-f39d-fe53-881f-72f25a816bca` · **Status:** `NEW`
- **Offering W12:** `RET-CMB-STUDENT` · **Split:** `test`

**KH**: Shop ơi combo sinh viên có bao nhiêu vậy?
**NV**: Dạ combo sinh viên gồm một trà và một bánh mì, giá 45k/combo nha bạn.
**KH**: Mình lấy hai combo. Có chọn trà đào được không?
**NV**: Mình ghi nhu cầu hai combo, tổng giá menu 90k. Trà nào được chọn trong combo mình cần xác nhận trước khi chốt nha.
**KH**: Nếu không có trà đào thì mình chọn theo danh sách bên bạn.
**NV**: Dạ mình sẽ báo danh sách áp dụng để bạn chọn, không tự thay món khi bạn chưa đồng ý.

### RET-046

- **Entity:** `Lead` · **UUID:** `00e02942-bc8d-075d-9fbb-dd6833d46878` · **Status:** `NEW`
- **Offering W12:** `RET-MAT-LATTE` · **Split:** `test`

**NV**: Dạ em chào anh, anh muốn uống gì ạ?
**KH**: Anh muốn thử matcha latte, nhưng chưa uống bao giờ.
**NV**: Dạ matcha latte 27k/ly anh nha. Anh muốn lấy một ly thử trước không ạ?
**KH**: Ừ một ly. Mà anh hỏi thêm, có loại nào cùng giá không?
**NV**: Dạ houjicha latte cũng 27k/ly. Anh có thể cân nhắc hai món theo sở thích, em không tự khẳng định vị nào dễ uống hơn nếu chưa có thông tin sản phẩm cụ thể ạ.
**KH**: Anh cứ chọn matcha latte.
**NV**: Dạ em ghi một matcha latte 27k, cảm ơn anh nhé.

### RET-047

- **Entity:** `Lead` · **UUID:** `00e9b3cc-a358-5d50-93b8-a5d7ef332e05` · **Status:** `QUALIFIED`
- **Offering W12:** `RET-CF-PHIN-SUA` · **Split:** `test`

**KH**: Em ơi, cà phê sữa phin máy 19k phải không?
**NV**: Dạ đúng rồi chị, 19k/ly ạ. Chị muốn đặt mấy ly?
**KH**: Một ly thôi. Chị còn định mua bánh croissant.
**NV**: Dạ croissant 35k/cái. Nếu mua lẻ hai món là 54k. Bên em cũng có combo sáng cà phê và croissant giá 55k, nên với hai món chị chọn thì mua lẻ đang rẻ hơn 1k ạ.
**KH**: Vậy lấy lẻ, em tư vấn có tâm ghê.
**NV**: Dạ em ghi một cà phê sữa phin máy và một croissant, tổng 54k, cảm ơn chị ạ.

### RET-048

- **Entity:** `Lead` · **UUID:** `0134e5ca-6f17-be56-8470-16f936a55a11` · **Status:** `NEW`
- **Offering W12:** `RET-FOOD-SANDWICH` · **Split:** `test`

**NV**: Dạ em chào chị, chị dùng gì ạ?
**KH**: Cho chị hai sandwich thịt nguội. Mà chị có thể đặt trước rồi đến lấy không?
**NV**: Dạ hai sandwich là 110k. Việc đặt trước và thời gian nhận cụ thể em cần xác nhận với cửa hàng, chị cho em biết khoảng thời gian mong muốn nhé.
**KH**: Khoảng mười hai giờ trưa, chị sợ chờ lâu.
**NV**: Dạ em ghi nhu cầu nhận lúc mười hai giờ, nhưng chưa hẹn chắc khi chưa kiểm tra khả năng chuẩn bị ạ.
**KH**: Ừ, báo được thì chị mới đặt.
**NV**: Dạ em kiểm tra xem cửa hàng có thể chuẩn bị để chị nhận lúc 12 giờ không rồi báo chị nha.

### RET-049

- **Entity:** `Lead` · **UUID:** `01405d65-aac7-de53-84a0-7db7a1d9c41f` · **Status:** `NEW`
- **Offering W12:** `RET-MEM-GOLD` · **Split:** `test`

**KH**: Chị ơi thẻ Gold bên mình giá 350k hả?
**NV**: Dạ đúng rồi em, thẻ thành viên Gold có giá niêm yết 350k/thẻ.
**KH**: Mua thẻ là được giảm đồ uống bao nhiêu phần trăm?
**NV**: Phần quyền lợi và mức giảm chị cần kiểm tra lại rồi báo em chính xác nhé.
**KH**: Em muốn biết rõ rồi mới mua.
**NV**: Ừ đúng rồi em. Chị sẽ kiểm tra quyền lợi và điều kiện áp dụng rồi báo rõ để em cân nhắc nha.

### RET-050

- **Entity:** `Lead` · **UUID:** `01472c99-9421-d857-ae1f-6eb681d277d6` · **Status:** `QUALIFIED`
- **Offering W12:** `RET-CF-KEM-MUOI` · **Split:** `test`

**NV**: Dạ em chào anh, anh muốn dùng gì hôm nay ạ?
**KH**: Cho anh cà phê kem muối, bao nhiêu em?
**NV**: Dạ cà phê kem muối 27k một ly anh nha. Anh muốn một ly phải không ạ?
**KH**: Ừ. Có bánh croissant không?
**NV**: Dạ có trong menu, 35k/cái. Anh dùng thêm thì tổng hai món là 62k ạ.
**KH**: Thêm bánh đi em, nhưng anh không cần giới thiệu món khác đâu.
**NV**: Dạ em hiểu, em ghi một cà phê kem muối với một croissant, tổng giá món 62k. Không gợi ý thêm nữa ạ.
**KH**: Đúng rồi, cảm ơn em.

## Hướng dẫn Codex sau khi duyệt

1. Đọc `AI_USAGE_DATA.md`, W12 canonical và mapping trong tài liệu; xác minh 50 UUID, organization, status, offering, price/unit và split 40 dev / 10 test (RET-050 là test theo quyết định pilot).
2. Giữ nguyên văn bản hội thoại và thứ tự lượt. Không tự sinh câu mới hoặc thay lời thoại. Nếu phát hiện mâu thuẫn W12, trả danh sách lỗi để duyệt.
3. Chỉ sau khi người dùng xác nhận nội dung mới generate deterministic channel accounts/conversations/messages/annotations, UUID, UTF-8, timestamps, dev/test và SHA256 theo chuẩn W13.
4. Gán intent/sentiment theo ngữ cảnh từng inbound, không heuristic từ khóa; lưu bảng nhãn tường minh và test semantic regression. Human-review những câu thật sự mơ hồ.
5. Không import DB, không sửa W12, không commit/push Git.
