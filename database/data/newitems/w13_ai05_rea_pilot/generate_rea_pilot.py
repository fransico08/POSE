#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate deterministic, synthetic W13 AI-05 POSE Realty pilot files."""
from __future__ import annotations

import argparse
import csv
import hashlib
import re
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
CANONICAL = ROOT / "database" / "data" / "seed" / "multisector_v2"
DEFAULT_OUTPUT = Path(__file__).resolve().parent
ORG = "47dcbd1f-c1c3-5130-8596-58d76fd2676e"
TEAM = "9880e2cb-aab4-5a7b-98bd-9d3392d747aa"
ASSIGNEE = "1139df81-a058-5bb2-aee6-6c479700293d"
NAMESPACE = uuid.UUID("6f3c5b31-1a20-5a71-a79d-93d9e5e730b4")
ANNOTATOR = "Nhi-round-1"
VERSION = "w13-ai05-rea-pilot-v1"
CHANNEL_ID = str(uuid.uuid5(NAMESPACE, "channel-account:zalo-sim-rea-ai05"))
START = datetime(2026, 11, 10, 8, 30, tzinfo=timezone.utc)
OUTPUT_FILES = ["channel_accounts.csv", "conversations.csv", "conversation_messages.csv", "round_1_annotations.csv", "rea_human_review.txt"]


@dataclass(frozen=True)
class Turn:
    direction: str
    content: str
    intent: str | None = None
    sentiment: str | None = None


APPROVED_DIALOGUES = DEFAULT_OUTPUT / "REA_50_hoi_thoai_bien_tap_001_050.md"


def approved_dialogues() -> dict[str, list[Turn]]:
    source = APPROVED_DIALOGUES.read_text(encoding="utf-8")
    result: dict[str, list[Turn]] = {}
    for block in re.split(r"(?=^## REA-\d{3} )", source, flags=re.MULTILINE):
        match = re.search(r"\*\*conversation_id:\*\* `([^`]+)`", block)
        if not match:
            continue
        turns = [Turn("INBOUND" if speaker == "KH" else "OUTBOUND", content.rstrip()) for speaker, content in re.findall(r"^\*\*(KH|NV):\*\* (.+?) {0,2}$", block, flags=re.MULTILINE)]
        if not turns or len(turns) % 2 or any(left.direction == right.direction for left, right in zip(turns, turns[1:])):
            raise RuntimeError(f"Invalid approved dialogue: {match.group(1)}")
        result[match.group(1)] = turns
    if len(result) != 50:
        raise RuntimeError(f"Expected 50 approved dialogues, found {len(result)}")
    return result


def label_inbound(content: str) -> tuple[str, str]:
    lowered = content.lower()
    if any(token in lowered for token in ("phàn", "sao hôm", "khó chịu", "bực", "không muốn nghe", "lệch nhu cầu")):
        return "COMPLAINT", "NEGATIVE"
    if any(token in lowered for token in ("phí", "giá", "bao nhiêu", "chi phí", "tiền cọc")):
        return "ASK_PRICE", "NEUTRAL"
    if any(token in lowered for token in ("khung giờ", "đặt lịch", "hẹn")):
        return "BOOKING", "NEUTRAL"
    if any(token in lowered for token in ("hướng dẫn", "bắt đầu", "chuẩn bị gì", "hồ sơ")):
        return "SUPPORT", "NEUTRAL"
    return "ASK_INFO", "NEUTRAL"


def scenario_turns(index: int, offering: dict[str, str]) -> tuple[str, list[Turn]]:
    workflow = "complaint" if index % 11 == 8 else workflow_for(offering)
    conversation_id = uid("conversation", index + 1)
    turns = []
    for turn in approved_dialogues()[conversation_id]:
        if turn.direction == "INBOUND":
            intent, sentiment = label_inbound(turn.content)
            turns.append(Turn(turn.direction, turn.content, intent, sentiment))
        else:
            turns.append(turn)
    return workflow, turns


def uid(kind: str, *parts: object) -> str:
    return str(uuid.uuid5(NAMESPACE, ":".join([kind, *(str(part) for part in parts)])))


def iso(value: datetime) -> str:
    return value.strftime("%Y-%m-%dT%H:%M:%SZ")


def csv_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def active_offerings() -> dict[str, dict[str, str]]:
    return {
        row["id"]: row
        for row in csv_rows(CANONICAL / "offerings.csv")
        if row["organization_id"] == ORG and row["status"] == "ACTIVE"
    }


def prices() -> dict[str, dict[str, str]]:
    return {
        row["offering_id"]: row
        for row in csv_rows(CANONICAL / "offering_prices.csv")
        if row["organization_id"] == ORG and not row["effective_to"]
    }


def property_attributes() -> dict[str, dict[str, str]]:
    definitions = {
        row["id"]: row["key"]
        for row in csv_rows(CANONICAL / "offering_attribute_definitions.csv")
        if row["organization_id"] == ORG
    }
    result: dict[str, dict[str, str]] = {}
    for row in csv_rows(CANONICAL / "offering_attribute_values.csv"):
        if row["organization_id"] == ORG:
            result.setdefault(row["offering_id"], {})[definitions[row["attribute_definition_id"]]] = row["value_json"].strip('"')
    return result


def profiles() -> list[tuple[str, dict[str, str]]]:
    customers = sorted((row for row in csv_rows(CANONICAL / "customers.csv") if row["organization_id"] == ORG and row["status"] == "ACTIVE" and not row["archived_at"]), key=lambda row: row["id"])[:35]
    leads = sorted((row for row in csv_rows(CANONICAL / "leads.csv") if row["organization_id"] == ORG and row["status"] in {"NEW", "QUALIFIED"} and not row["archived_at"]), key=lambda row: row["id"])[:15]
    if len(customers) != 35 or len(leads) != 15:
        raise RuntimeError("Insufficient eligible REA profiles in canonical W12.")
    return [("CUSTOMER", row) for row in customers] + [("LEAD", row) for row in leads]


def offering_for(index: int, profile_type: str, profile: dict[str, str]) -> dict[str, str]:
    offerings = active_offerings()
    linked = profile["offering_id"] if profile_type == "LEAD" else profile["related_offering_id"]
    if linked in offerings:
        return offerings[linked]
    return sorted(offerings.values(), key=lambda row: row["code"])[index % len(offerings)]


def price_text(offering: dict[str, str]) -> str:
    price = prices()[offering["id"]]
    amount = f"{int(price['amount']):,}".replace(",", ".")
    return f"{amount} {price['currency_code']}/{price['pricing_unit'].lstrip('/')}"


def workflow_for(offering: dict[str, str]) -> str:
    code = offering["code"]
    if code.startswith(("APT", "TOWN")):
        return "property_inquiry"
    if code.startswith("REA-RS"):
        return "sales_brokerage"
    if code.startswith("REA-RNT"):
        return "rental_brokerage"
    if code.startswith("REA-PM"):
        return "property_management"
    if code.startswith("REA-LF"):
        return "legal_finance"
    if code.startswith("REA-VAL"):
        return "valuation"
    return "consultation"


def property_fact(offering: dict[str, str]) -> str:
    values = property_attributes()[offering["id"]]
    return f"{values['area_m2']} m2, {values['bedrooms']} phòng ngủ, khu vực {values['district']}"


def pick(index: int, choices: list[str]) -> str:
    return choices[index % len(choices)]


def property_turns(index: int, offering: dict[str, str]) -> list[Turn]:
    concerns = [
        "vợ chồng trẻ muốn xem thông tin trước khi cân đối tài chính",
        "gia đình đang cần thêm không gian nhưng chưa muốn quyết định vội",
        "người mua ưu tiên khu vực trước rồi mới tính phương án phù hợp",
        "khách muốn hiểu tài sản có hợp nhu cầu ở lâu dài hay không",
        "người mua đang so lại diện tích với nhu cầu sinh hoạt thực tế",
    ]
    endings = [
        "Mình chưa hỏi lịch xem, chỉ cần biết các thông tin đang được lưu.",
        "Nếu phù hợp hơn, mình sẽ nhắn lại để trao đổi thêm.",
        "Mình cần trao đổi với gia đình trước khi đi tiếp.",
        "Mình không cần giữ chỗ hay cam kết gì ở bước này.",
        "Mình muốn cân nhắc kỹ rồi mới đề nghị một buổi tư vấn.",
    ]
    price_questions = [
        f"Mức {price_text(offering)} là giá niêm yết của tài sản này phải không?",
        f"Mình muốn xác nhận: {price_text(offering)} đang là giá của {offering['name']} hay là mức tham khảo?",
        f"Giá {price_text(offering)} bên mình đang lưu có bao gồm khoản nào khác không?",
        f"Cho mình hỏi lại, {price_text(offering)} là giá tài sản chứ không phải phí môi giới, đúng không?",
        f"Mức {price_text(offering)} hiện chỉ là thông tin niêm yết, phải không bạn?",
    ]
    price_replies = [
        f"Dạ, {price_text(offering)} là giá niêm yết đang lưu cho {offering['name']}. Các khoản hoặc điều kiện khác chưa có dữ liệu xác minh, nên em không tự suy diễn thêm.",
        f"Dạ, thông tin hiện có ghi {offering['name']} ở mức {price_text(offering)}. Em chưa có căn cứ để khẳng định thêm các khoản khác.",
        f"Dạ, mức {price_text(offering)} đang được ghi cho tài sản. Nếu cần làm rõ khoản phát sinh hoặc điều kiện, em cần kiểm tra nguồn phù hợp trước.",
        f"Dạ đúng, đây là giá niêm yết của {offering['name']}; không phải phí dịch vụ. Phần điều kiện đi kèm chưa được xác minh trong cuộc chat này.",
        f"Dạ, em chỉ có thể xác nhận mức {price_text(offering)} đang lưu cho {offering['name']}. Thông tin khác cần được kiểm tra riêng.",
    ]
    follow_ups = [
        f"Dạ, khi anh/chị cần trao đổi tiếp về {offering['name']}, em sẽ kiểm tra đúng phần anh/chị quan tâm.",
        f"Dạ, em đã hiểu anh/chị mới đang tham khảo {offering['name']}; khi cần thêm thông tin có thể xác minh, anh/chị cứ nhắn lại.",
        f"Dạ, em sẽ giữ cuộc trao đổi ở mức thông tin hiện có về {offering['name']}, không mặc định anh/chị cần xem nhà hay đặt chỗ.",
        f"Dạ, anh/chị cứ cân nhắc. Khi có câu hỏi cụ thể hơn về {offering['name']}, em sẽ kiểm tra trước khi trả lời.",
        f"Dạ, em đã lưu ý nhu cầu tìm hiểu của anh/chị về {offering['name']}; chưa có bước nào được xác nhận từ cuộc chat này.",
    ]
    return [
        Turn("INBOUND", f"Mình đang xem {offering['name']}. {concerns[index % len(concerns)]}; bên mình xác nhận thông tin cơ bản giúp mình được không?", "ASK_INFO", "NEUTRAL"),
        Turn("OUTBOUND", f"Dạ, thông tin hiện có về {offering['name']} là {property_fact(offering)}, giá niêm yết {price_text(offering)}. Em chưa thể xác nhận tình trạng thực tế hay lịch xem khi chưa có nguồn xác minh."),
        Turn("INBOUND", pick(index, price_questions), "ASK_PRICE", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, price_replies)),
        Turn("INBOUND", endings[index % len(endings)], "OTHER", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, follow_ups)),
    ]


def brokerage_turns(index: int, offering: dict[str, str], rental: bool) -> list[Turn]:
    concerns = (
        ["muốn bán tài sản nhưng chưa rõ bắt đầu từ đâu", "đang cân giữa tự tìm khách và dùng dịch vụ", "muốn hỏi phạm vi hỗ trợ trước khi chia sẻ thêm thông tin", "cần người thân cùng xem lại chi phí dịch vụ", "chưa muốn cam kết khi chưa hiểu các bước làm việc"]
        if not rental
        else ["đang tìm chỗ thuê nhưng chưa chốt khu vực", "muốn hiểu phí môi giới trước khi gửi nhu cầu cụ thể", "cần cân việc thuê với thời gian chuyển chỗ", "đang hỗ trợ người thân tìm nhà nhưng chưa có quyết định", "muốn biết phạm vi hỗ trợ chứ chưa đề nghị tìm ngay"]
    )
    stages = [
        "Anh/chị cho em biết nhu cầu đang ở bước tìm hiểu hay đã có thông tin cụ thể, em sẽ kiểm tra phạm vi phù hợp.",
        "Nếu anh/chị mới tham khảo, em có thể làm rõ cách tiếp nhận trước; chưa cần gửi chi tiết tài sản ngay.",
        "Anh/chị nêu phần cần hỗ trợ trước, em sẽ xác định xem dịch vụ có phù hợp hay không.",
        "Em có thể ghi nhận mục tiêu ban đầu, nhưng chưa thể khẳng định kết quả giao dịch qua chat.",
        "Khi anh/chị có thêm thông tin, em sẽ kiểm tra phần có thể hỗ trợ thay vì giả định nhu cầu.",
    ]
    price_questions = [
        f"Phí {price_text(offering)} được tính cho dịch vụ này theo cách nào?",
        f"Mức {price_text(offering)} là phí dịch vụ chứ không phải giá bất động sản, đúng không?",
        f"Cho mình xác nhận lại, {price_text(offering)} là mức đang ghi cho một giao dịch phải không?",
        f"Khoản {price_text(offering)} mình đang thấy có phải phí môi giới của dịch vụ này không?",
        f"Mức phí {price_text(offering)} áp cho phần hỗ trợ nào, bạn có thể xác nhận theo thông tin đang có không?",
    ]
    price_replies = [
        f"Dạ, mức {price_text(offering)} đang ghi là phí dịch vụ của {offering['name']}, không phải giá bất động sản hay giá thuê. Điều kiện áp dụng cần kiểm tra theo tình huống tiếp nhận.",
        f"Dạ, {price_text(offering)} là mức phí dịch vụ đang lưu. Em chưa có cơ sở để xác nhận phạm vi áp dụng cho trường hợp cụ thể của anh/chị.",
        f"Dạ, đây là phí theo đơn vị ghi nhận của dịch vụ, không phải giá tài sản. Phần điều kiện sẽ cần được đối chiếu trước khi tư vấn tiếp.",
        f"Dạ đúng, mức {price_text(offering)} thuộc dịch vụ môi giới. Em không thể suy ra chi phí khác khi chưa có thông tin tiếp nhận.",
        f"Dạ, em xác nhận đây là phí dịch vụ đang ghi. Việc có áp dụng hay không cần được kiểm tra khi hiểu rõ nhu cầu của anh/chị.",
    ]
    booking_requests = [
        "Mình muốn trao đổi ngắn để làm rõ nhu cầu. Bên mình có thể kiểm tra khung giờ qua Zalo này không?",
        "Nếu có thể sắp xếp, mình muốn đặt một buổi tư vấn về phạm vi dịch vụ; bạn kiểm tra giúp khung giờ phù hợp nhé.",
        "Mình chưa cần chốt dịch vụ, chỉ muốn hỏi khả năng hẹn trao đổi để nói rõ nhu cầu.",
        "Bạn cho mình biết cách gửi khung giờ mong muốn để bên mình kiểm tra lịch tư vấn được không?",
        "Mình muốn đề nghị một cuộc trao đổi trước khi quyết định; thời gian chỉ tính là dự kiến đến khi bên mình xác nhận.",
    ]
    booking_replies = [
        f"Dạ, anh/chị gửi vài khung giờ thuận tiện. Em sẽ kiểm tra khả năng sắp xếp trao đổi về {offering['name']}; đây chưa phải lịch đã xác nhận.",
        f"Dạ, anh/chị có thể nhắn khung giờ mong muốn tại đây. Em sẽ kiểm tra với bên phụ trách trước khi phản hồi về việc trao đổi {offering['name']}.",
        f"Dạ, em ghi nhận nhu cầu trao đổi. Khi anh/chị gửi thời gian phù hợp, em chỉ xác nhận sau khi kiểm tra được khả năng sắp xếp.",
        f"Dạ, anh/chị cứ gửi thời gian dự kiến. Em sẽ kiểm tra xem có thể bố trí trao đổi về {offering['name']} hay không, không tự chốt lịch qua tin nhắn này.",
        f"Dạ, em có thể tiếp nhận đề nghị lịch tại đây. Việc có buổi trao đổi chỉ được xác nhận sau khi kiểm tra khung giờ phù hợp.",
    ]
    return [
        Turn("INBOUND", f"Mình đang tìm hiểu {offering['name']}. Mình {concerns[index % len(concerns)]}; bên mình tư vấn phần nào được?", "ASK_INFO", "NEUTRAL"),
        Turn("OUTBOUND", f"Dạ, {offering['name']} là dịch vụ theo giao dịch. {pick(index, stages)}"),
        Turn("INBOUND", pick(index, price_questions), "ASK_PRICE", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, price_replies)),
        Turn("INBOUND", pick(index, booking_requests), "BOOKING", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, booking_replies)),
    ]


def management_turns(index: int, offering: dict[str, str]) -> list[Turn]:
    concerns = ["chủ nhà ở xa nên cần đầu mối theo dõi công việc", "đang cân giữa tự quản lý và thuê hỗ trợ", "muốn làm rõ phạm vi trước khi chuyển thông tin tài sản", "cần cách xử lý khi có việc phát sinh nhưng chưa chọn dịch vụ", "đang xem lại chi phí vận hành trong vài tháng tới"]
    service_replies = [
        f"Dạ, thông tin hiện có ghi {offering['name']} theo đơn vị {price_text(offering)}. Phạm vi thực tế cần được xác nhận theo nhu cầu, nên em không nói thay cho một gói chưa kiểm tra.",
        f"Dạ, mức đang lưu cho {offering['name']} là {price_text(offering)}. Anh/chị có thể nêu việc cần theo dõi để em kiểm tra phạm vi trước.",
        f"Dạ, em chỉ có thể xác nhận dịch vụ và mức {price_text(offering)} đang ghi. Cách xử lý từng việc phát sinh cần được làm rõ khi tiếp nhận.",
        f"Dạ, {offering['name']} hiện được ghi theo {price_text(offering)}. Em cần biết nhu cầu cụ thể trước khi nói dịch vụ có bao gồm việc nào.",
        f"Dạ, em có thông tin mức {price_text(offering)} của {offering['name']}; phần công việc phù hợp cần được kiểm tra theo yêu cầu thực tế.",
    ]
    price_questions = [
        f"Mức {price_text(offering)} là phí đang ghi cho dịch vụ này, đúng không?",
        f"Cho mình xác nhận lại, {price_text(offering)} là mức theo đơn vị hiện có phải không?",
        f"Phí {price_text(offering)} có phải thông tin đang lưu cho {offering['name']} không?",
        f"Mức {price_text(offering)} bên mình đang ghi là phí dịch vụ, không phải dự toán phát sinh, đúng không?",
        f"Bạn xác nhận giúp mình mức {price_text(offering)} hiện tại đang được ghi cho dịch vụ nhé.",
    ]
    price_replies = [
        f"Dạ, {price_text(offering)} là mức đang ghi cho {offering['name']}. Điều kiện áp dụng cần được kiểm tra khi có yêu cầu cụ thể.",
        f"Dạ đúng, em chỉ xác nhận mức {price_text(offering)} trong dữ liệu hiện có; chưa thể nói thêm về phần chưa được tiếp nhận.",
        f"Dạ, đây là mức phí đang lưu của dịch vụ. Em sẽ không coi đó là cam kết cho một phạm vi công việc chưa được xác định.",
        f"Dạ, mức {price_text(offering)} đang được ghi nhận. Nếu anh/chị có yêu cầu cụ thể, em sẽ kiểm tra điều kiện trước khi trả lời.",
        f"Dạ, em xác nhận mức phí đang lưu là {price_text(offering)}. Phạm vi cần được đối chiếu riêng theo nhu cầu của anh/chị.",
    ]
    support_questions = [
        "Hiện mình chưa gửi yêu cầu cụ thể. Nếu cần, mình có thể tiếp tục hỏi qua Zalo này chứ?",
        "Mình muốn hỏi thêm sau khi rà soát tài sản; đầu mối này có tiếp nhận nội dung tiếp theo không?",
        "Trước mắt mình chỉ tìm hiểu. Khi có việc cần làm rõ, mình nhắn lại tại đây được chứ?",
        "Mình chưa chọn dịch vụ ngay. Bên mình có thể nhận phần mô tả nhu cầu qua Zalo này không?",
        "Mình cần thời gian xem lại. Khi có yêu cầu cụ thể, mình gửi qua cuộc chat này được không?",
    ]
    support_replies = [
        f"Dạ, anh/chị có thể tiếp tục nhắn tại đây. Khi có yêu cầu cụ thể về {offering['name']}, em sẽ kiểm tra đúng phạm vi.",
        f"Dạ được ạ. Anh/chị cứ gửi phần cần làm rõ qua Zalo này, em sẽ dựa trên nội dung đó để kiểm tra thông tin phù hợp.",
        f"Dạ, cuộc chat này có thể tiếp nhận câu hỏi tiếp theo. Em sẽ không mặc định anh/chị đã sử dụng {offering['name']}.",
        f"Dạ, anh/chị có thể nhắn lại khi sẵn sàng. Em sẽ xem yêu cầu cụ thể rồi mới xác nhận phần nào có thể hỗ trợ.",
        f"Dạ, em có thể ghi nhận nội dung qua đây. Việc tiếp nhận hay phạm vi hỗ trợ sẽ được kiểm tra theo yêu cầu thực tế.",
    ]
    return [
        Turn("INBOUND", f"Mình đang cân nhắc {offering['name']}. Mình {concerns[index % len(concerns)]}; dịch vụ thường hỗ trợ theo hướng nào?", "ASK_INFO", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, service_replies)),
        Turn("INBOUND", pick(index, price_questions), "ASK_PRICE", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, price_replies)),
        Turn("INBOUND", pick(index, support_questions), "SUPPORT", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, support_replies)),
    ]


def legal_turns(index: int, offering: dict[str, str]) -> list[Turn]:
    cases = ["gia đình cần hiểu dịch vụ trước khi chuẩn bị hồ sơ", "mình đang lo thiếu giấy tờ nhưng chưa muốn kết luận điều gì", "mình muốn hỏi trình tự chung, không yêu cầu tư vấn pháp lý ngay trong chat", "người thân nhờ tìm hiểu trước khi quyết định dùng dịch vụ", "mình cần biết phạm vi tiếp nhận để không gửi nhầm thông tin nhạy cảm"]
    price_questions = [
        f"Mức {price_text(offering)} là phí dịch vụ đang ghi phải không?",
        f"Cho mình xác nhận {price_text(offering)} là mức phí theo hồ sơ/hợp đồng của dịch vụ này, đúng không?",
        f"Phí {price_text(offering)} đang được áp theo thông tin hiện có như thế nào?",
        f"Mức {price_text(offering)} là phí dịch vụ chứ không phải khoản nhà nước, phải không bạn?",
        f"Bạn xác nhận giúp mình mức {price_text(offering)} đang lưu cho {offering['name']} nhé.",
    ]
    price_replies = [
        f"Dạ, {price_text(offering)} là mức phí đang ghi cho {offering['name']}. Các điều kiện cụ thể cần được kiểm tra trước khi tư vấn tiếp.",
        f"Dạ đúng, đây là phí dịch vụ theo thông tin hiện có; em không thể kết luận khoản nào khác khi chưa tiếp nhận hồ sơ.",
        f"Dạ, em xác nhận mức {price_text(offering)} đang lưu. Phạm vi áp dụng cần được đối chiếu theo nội dung anh/chị cần hỗ trợ.",
        f"Dạ, mức này thuộc dịch vụ của bên em. Phần nghĩa vụ hoặc giấy tờ liên quan cần được kiểm tra riêng, không thể kết luận trong chat.",
        f"Dạ, {price_text(offering)} là phí đang ghi. Nếu anh/chị tiếp tục tìm hiểu, em sẽ kiểm tra điều kiện trước khi trả lời thêm.",
    ]
    booking_requests = [
        "Mình muốn trao đổi để nói rõ hồ sơ; bạn kiểm tra giúp khả năng sắp xếp khung giờ được không?",
        "Nếu có khung giờ phù hợp, mình muốn đề nghị một buổi trao đổi trước khi gửi thông tin chi tiết.",
        "Mình cần hỏi thêm về trường hợp của mình. Bên mình có thể kiểm tra lịch tư vấn qua Zalo này chứ?",
        "Bạn cho mình biết cách gửi thời gian dự kiến để bên mình xem có thể sắp xếp trao đổi không?",
        "Mình chưa cần chốt dịch vụ; chỉ muốn hỏi khả năng hẹn trao đổi khi bên mình xác nhận được thời gian.",
    ]
    booking_replies = [
        f"Dạ, anh/chị gửi khung giờ phù hợp. Em sẽ kiểm tra khả năng sắp xếp trao đổi cho {offering['name']}; đây chưa phải lịch đã xác nhận.",
        f"Dạ, anh/chị có thể nhắn thời gian dự kiến. Em sẽ kiểm tra trước khi phản hồi về việc trao đổi {offering['name']}.",
        f"Dạ, em tiếp nhận đề nghị lịch tại đây. Chỉ sau khi kiểm tra được khung giờ phù hợp, em mới xác nhận buổi trao đổi.",
        f"Dạ, anh/chị cứ gửi vài khung giờ thuận tiện. Em không tự chốt lịch khi chưa kiểm tra khả năng sắp xếp.",
        f"Dạ, em sẽ kiểm tra khung giờ theo đề nghị của anh/chị. Cuộc hẹn chỉ được xác nhận sau bước kiểm tra này.",
    ]
    return [
        Turn("INBOUND", f"Mình đang tìm hiểu {offering['name']}; {cases[index % len(cases)]}. Bên mình cho mình biết dịch vụ tiếp nhận vấn đề theo cách nào?", "ASK_INFO", "NEUTRAL"),
        Turn("OUTBOUND", f"Dạ, mức đang ghi cho {offering['name']} là {price_text(offering)}. Phạm vi hồ sơ hoặc tư vấn cần được xác nhận khi tiếp nhận, nên em không thể kết luận về tình trạng pháp lý qua tin nhắn này."),
        Turn("INBOUND", pick(index, price_questions), "ASK_PRICE", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, price_replies)),
        Turn("INBOUND", pick(index, booking_requests), "BOOKING", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, booking_replies)),
    ]


def valuation_turns(index: int, offering: dict[str, str]) -> list[Turn]:
    concerns = ["mình cần một cơ sở để bàn bạc trong gia đình", "mình đang cân nhắc một quyết định cá nhân", "doanh nghiệp cần tham khảo trước khi lập kế hoạch", "mình muốn biết dịch vụ có phù hợp với loại tài sản quan tâm không", "mình chưa muốn gửi hồ sơ khi chưa hiểu phạm vi dịch vụ"]
    service_replies = [
        f"Dạ, {offering['name']} là dịch vụ thẩm định hoặc tham khảo theo thông tin hiện có. Em có thể kiểm tra cách tiếp nhận, nhưng không thể đưa ra giá trị tài sản hoặc kết quả thẩm định trong chat.",
        f"Dạ, em có thể ghi nhận nhu cầu để kiểm tra cách tiếp nhận của {offering['name']}. Giá trị tài sản và kết quả thẩm định không thể xác nhận qua tin nhắn.",
        f"Dạ, dịch vụ này cần được xem theo thông tin tiếp nhận thực tế. Em không thể đưa ra kết quả thẩm định hay giá trị tài sản khi chưa có bước kiểm tra phù hợp.",
        f"Dạ, em có thể làm rõ phạm vi thông tin ban đầu của {offering['name']}; còn kết quả thẩm định cần được xác minh theo quy trình phù hợp.",
        f"Dạ, anh/chị có thể nêu loại nhu cầu đang quan tâm. Em sẽ kiểm tra hướng tiếp nhận, không suy ra giá trị tài sản hoặc kết quả từ cuộc chat này.",
    ]
    price_questions = [
        f"Mức {price_text(offering)} là phí dịch vụ chứ không phải giá tài sản, đúng không?",
        f"Cho mình xác nhận {price_text(offering)} là phí đang ghi của dịch vụ này nhé.",
        f"Mức {price_text(offering)} được hiểu là chi phí cho dịch vụ, không phải mức định giá, phải không?",
        f"Phí {price_text(offering)} bên mình đang lưu có phải dành cho lần thực hiện dịch vụ không?",
        f"Bạn xác nhận giúp mình: {price_text(offering)} là phí dịch vụ của {offering['name']}?",
    ]
    price_replies = [
        f"Dạ đúng, {price_text(offering)} là phí dịch vụ của {offering['name']}, không phải giá tài sản. Điều kiện áp dụng sẽ cần xác minh thêm.",
        f"Dạ, mức {price_text(offering)} là phí đang ghi cho dịch vụ. Đây không phải kết quả hay giá trị thẩm định của tài sản.",
        f"Dạ, em xác nhận đây là chi phí dịch vụ theo dữ liệu hiện có; giá trị tài sản chỉ có thể xem xét khi có bước phù hợp.",
        f"Dạ, {price_text(offering)} thuộc phần phí dịch vụ. Em không thể dùng mức đó để suy ra giá trị tài sản.",
        f"Dạ, đây là mức phí đang lưu của {offering['name']}. Điều kiện tiếp nhận sẽ cần kiểm tra trước khi nói thêm.",
    ]
    next_steps = [
        "Mình sẽ xem lại thông tin trước. Khi cần trao đổi kỹ hơn, mình sẽ chủ động đề nghị lịch tư vấn.",
        "Trước mắt mình chỉ muốn hiểu phạm vi dịch vụ; nếu phù hợp mình sẽ nhắn lại để hỏi bước tiếp theo.",
        "Mình cần bàn thêm với gia đình rồi mới quyết định có trao đổi tiếp hay không.",
        "Mình chưa gửi hồ sơ lúc này. Khi xác định rõ nhu cầu, mình sẽ liên hệ lại.",
        "Cảm ơn bạn, mình sẽ cân nhắc thêm và chỉ hỏi tiếp khi có thông tin cụ thể hơn.",
    ]
    next_replies = [
        f"Dạ, anh/chị cứ xem lại. Khi có nhu cầu rõ hơn về {offering['name']}, em sẽ kiểm tra bước tiếp theo phù hợp.",
        f"Dạ, em hiểu anh/chị mới đang tham khảo. Khi anh/chị nhắn lại, em sẽ dựa trên phần đã trao đổi để kiểm tra thêm.",
        f"Dạ, anh/chị không cần quyết định trong cuộc chat này. Em sẽ không mặc định đã có hồ sơ hay yêu cầu thẩm định.",
        f"Dạ, khi nhu cầu được làm rõ hơn, anh/chị cứ nhắn lại. Em sẽ kiểm tra thông tin cần thiết trước khi phản hồi.",
        f"Dạ, em đã ghi nhận đây là trao đổi tham khảo về {offering['name']}; chưa có bước nào được xác nhận.",
    ]
    return [
        Turn("INBOUND", f"Mình đang tìm hiểu {offering['name']}; {concerns[index % len(concerns)]}. Dịch vụ này tiếp nhận yêu cầu như thế nào?", "ASK_INFO", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, service_replies)),
        Turn("INBOUND", pick(index, price_questions), "ASK_PRICE", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, price_replies)),
        Turn("INBOUND", pick(index, next_steps), "OTHER", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, next_replies)),
    ]


def consultation_turns(index: int, offering: dict[str, str]) -> list[Turn]:
    concerns = ["mình muốn định hình nhu cầu trước khi chi tiền", "gia đình đang có hai phương án nhưng chưa muốn chọn vội", "mình cần một cuộc trao đổi có trọng tâm thay vì lời khuyên chung", "mình đang cân giữa việc tự xử lý và nhờ chuyên môn", "mình cần biết dịch vụ có đúng mục tiêu đang quan tâm không"]
    first_replies = [
        f"Dạ, anh/chị có thể nêu mục tiêu và phần đang vướng. Em sẽ kiểm tra thông tin hiện có về {offering['name']}; chưa thể hứa kết quả khi chưa tiếp nhận đủ bối cảnh.",
        f"Dạ, anh/chị cho em biết điều cần cân nhắc trước. Em sẽ xem {offering['name']} có phù hợp với nhu cầu mô tả hay không.",
        f"Dạ, em có thể tiếp nhận phần mục tiêu và vướng mắc cụ thể. Khuyến nghị chi tiết cần dựa trên thông tin được xác minh.",
        f"Dạ, anh/chị nêu giúp điểm cần làm rõ. Em sẽ chỉ phản hồi trong phạm vi thông tin hiện có của {offering['name']}.",
        f"Dạ, em hiểu anh/chị cần trao đổi có trọng tâm. Em sẽ kiểm tra nhu cầu trước, không đưa ra cam kết kết quả qua chat.",
    ]
    price_questions = [
        f"Mức {price_text(offering)} đang ghi cho {offering['name']}, mình hiểu vậy đúng không?",
        f"Cho mình xác nhận phí {price_text(offering)} của dịch vụ này theo thông tin hiện có nhé.",
        f"Mức {price_text(offering)} là chi phí dịch vụ đang ghi, phải không bạn?",
        f"Phí {price_text(offering)} mình thấy có thuộc {offering['name']} không?",
        f"Bạn xác nhận giúp mình mức {price_text(offering)} đang lưu cho phần tư vấn này.",
    ]
    price_replies = [
        f"Dạ, mức đang ghi cho {offering['name']} là {price_text(offering)}. Phạm vi áp dụng cần xác nhận khi tiếp nhận.",
        f"Dạ đúng, em chỉ xác nhận mức {price_text(offering)} trong dữ liệu hiện có; điều kiện cụ thể cần được kiểm tra thêm.",
        f"Dạ, {price_text(offering)} là mức phí đang lưu. Em không thể coi đó là cam kết khi chưa hiểu đủ nhu cầu của anh/chị.",
        f"Dạ, em xác nhận mức phí đang ghi là {price_text(offering)}. Phần áp dụng thực tế cần được đối chiếu riêng.",
        f"Dạ, đó là thông tin phí hiện có của dịch vụ. Khi tiếp nhận nội dung cụ thể, em sẽ kiểm tra điều kiện phù hợp.",
    ]
    pauses = [
        "Mình sẽ xem lại nhu cầu trước, chưa cần quyết định dịch vụ trong hôm nay.",
        "Mình cần trao đổi thêm với gia đình rồi mới nhắn lại.",
        "Cảm ơn bạn, hiện mình chỉ đang so sánh các phương án phù hợp với nhu cầu.",
        "Mình chưa muốn đi sâu thêm. Khi có câu hỏi cụ thể, mình sẽ chủ động liên hệ lại.",
        "Mình ghi nhận thông tin này trước; quyết định có tiếp tục hay không mình sẽ cân nhắc sau.",
    ]
    pause_replies = [
        f"Dạ, anh/chị cứ cân nhắc. Khi liên hệ lại về {offering['name']}, em sẽ dựa trên nội dung đã trao đổi để kiểm tra tiếp.",
        f"Dạ, em hiểu. Anh/chị không cần chốt trong cuộc chat này; khi có nhu cầu rõ hơn, em sẽ hỗ trợ trong phạm vi có thể xác minh.",
        f"Dạ, em đã ghi nhận đây là bước tham khảo. Em không mặc định anh/chị đã chọn hoặc sử dụng {offering['name']}.",
        f"Dạ, anh/chị cứ xem lại. Khi có phần cần làm rõ, em sẽ kiểm tra thông tin trước khi phản hồi.",
        f"Dạ, em tôn trọng thời gian cân nhắc của anh/chị. Hiện chưa có bước nào được xác nhận từ cuộc trao đổi này.",
    ]
    return [
        Turn("INBOUND", f"Mình muốn tìm hiểu {offering['name']}. {concerns[index % len(concerns)]}; bên mình cần biết gì để kiểm tra phù hợp?", "ASK_INFO", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, first_replies)),
        Turn("INBOUND", pick(index, price_questions), "ASK_PRICE", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, price_replies)),
        Turn("INBOUND", pick(index, pauses), "OTHER", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, pause_replies)),
    ]


def complaint_turns(index: int, offering: dict[str, str]) -> list[Turn]:
    issues = ["lần trước nhận hai cách giải thích khác nhau về phạm vi dịch vụ", "đã hỏi thông tin nhưng câu trả lời trước còn quá chung chung", "đang khó chịu vì phải nhắc lại nhu cầu từ đầu", "chưa rõ khoản phí đang ghi được áp dụng trong trường hợp nào", "không muốn nhận lời hứa khi phần cần xác nhận vẫn chưa rõ"]
    issue = issues[index % len(issues)]
    first_replies = [
        f"Dạ, em xin lỗi vì thông tin về {offering['name']} chưa rõ. Em sẽ đối chiếu đúng nội dung anh/chị nêu và chỉ phản hồi phần đã xác minh.",
        f"Dạ, em hiểu việc nhận thông tin không thống nhất gây khó chịu. Em sẽ bám vào cuộc chat này để làm rõ phần liên quan đến {offering['name']}.",
        f"Dạ, em xin lỗi vì anh/chị phải nhắc lại. Em sẽ xem phần đã trao đổi trước khi hỏi thêm nội dung thật sự cần thiết.",
        f"Dạ, em ghi nhận phản ánh về {offering['name']}. Em sẽ không khẳng định điều chưa kiểm tra, và sẽ làm rõ đúng điểm anh/chị đang vướng.",
        f"Dạ, em hiểu anh/chị cần câu trả lời có căn cứ. Em sẽ rà lại thông tin hiện có thay vì đưa thêm lời hứa chưa xác nhận.",
    ]
    follow_up_complaints = [
        "Mình không cần xử lý ngay, nhưng mong đừng bắt mình kể lại toàn bộ khi nội dung đã có trong cuộc chat này.",
        "Mình chỉ cần bên bạn trả lời đúng điểm đang vướng, không cần tư vấn lan sang phần khác.",
        "Nếu cần hỏi thêm, bạn nói rõ giúp mình đang thiếu thông tin nào để mình chủ động cung cấp.",
        "Mình mong nội dung sau được thống nhất, vì mình không muốn nhận thêm hai cách giải thích khác nhau.",
        "Mình chưa quyết định dùng dịch vụ; trước hết mình cần phần phản ánh này được làm rõ.",
    ]
    follow_up_replies = [
        f"Dạ, em sẽ bám theo nội dung đang có về {offering['name']} và chỉ hỏi thêm khi cần làm rõ, không yêu cầu anh/chị kể lại từ đầu.",
        f"Dạ, em sẽ tập trung vào đúng điểm anh/chị phản ánh. Phần nào chưa có căn cứ, em sẽ nói rõ là cần kiểm tra.",
        f"Dạ, nếu cần thêm thông tin, em sẽ nêu cụ thể nội dung cần đối chiếu; không mở rộng sang phần anh/chị chưa hỏi.",
        f"Dạ, em sẽ rà lại để phản hồi nhất quán theo thông tin có thể xác minh, không tự suy đoán thêm.",
        f"Dạ, em hiểu. Trước khi nói đến bước tiếp theo của {offering['name']}, em sẽ ưu tiên làm rõ phản ánh hiện tại.",
    ]
    closings = [
        "Khi phần này rõ ràng hơn, mình sẽ tự cân nhắc có tiếp tục trao đổi không.",
        "Mình chờ phần giải thích thống nhất rồi mới quyết định bước tiếp theo.",
        "Trước mắt bạn cứ làm rõ đúng nội dung này, còn việc dùng dịch vụ mình sẽ tính sau.",
        "Mình chưa cần hẹn hay chốt gì. Mình chỉ muốn câu trả lời sau có căn cứ.",
        "Nếu thông tin được làm rõ, mình sẽ xem lại và phản hồi khi phù hợp.",
    ]
    closing_replies = [
        f"Dạ, em sẽ tập trung làm rõ phản ánh liên quan đến {offering['name']}; quyền quyết định có tiếp tục hay không vẫn do anh/chị cân nhắc.",
        f"Dạ, em hiểu. Em chỉ ghi nhận yêu cầu làm rõ, không mặc định anh/chị muốn đặt lịch hoặc sử dụng dịch vụ.",
        f"Dạ, em sẽ kiểm tra phần cần xác minh trước. Khi có câu trả lời rõ hơn, anh/chị tự quyết định bước tiếp theo.",
        f"Dạ, em đã lưu ý phản ánh của anh/chị về {offering['name']}. Hiện chưa có cam kết hay lịch hẹn nào được xác nhận.",
        f"Dạ, em tôn trọng việc anh/chị cần câu trả lời rõ ràng trước. Em sẽ không gây áp lực cho một quyết định trong cuộc chat này.",
    ]
    return [
        Turn("INBOUND", f"Mình cần phản ánh về {offering['name']}: {issue}. Mình muốn có câu trả lời thống nhất.", "COMPLAINT", "NEGATIVE"),
        Turn("OUTBOUND", pick(index, first_replies)),
        Turn("INBOUND", pick(index, follow_up_complaints), "COMPLAINT", "NEGATIVE"),
        Turn("OUTBOUND", pick(index, follow_up_replies)),
        Turn("INBOUND", pick(index, closings), "OTHER", "NEUTRAL"),
        Turn("OUTBOUND", pick(index, closing_replies)),
    ]


def turns_for(index: int, offering: dict[str, str]) -> tuple[str, list[Turn]]:
    return scenario_turns(index, offering)


def legacy_turns_for(index: int, offering: dict[str, str]) -> tuple[str, list[Turn]]:
    workflow = workflow_for(offering)
    if index % 11 == 8:
        return "complaint", complaint_turns(index, offering)
    if workflow == "property_inquiry":
        return workflow, property_turns(index, offering)
    if workflow == "sales_brokerage":
        return workflow, brokerage_turns(index, offering, rental=False)
    if workflow == "rental_brokerage":
        return workflow, brokerage_turns(index, offering, rental=True)
    if workflow == "property_management":
        return workflow, management_turns(index, offering)
    if workflow == "legal_finance":
        return workflow, legal_turns(index, offering)
    if workflow == "valuation":
        return workflow, valuation_turns(index, offering)
    return workflow, consultation_turns(index, offering)


def priority_review_turns(index: int, offering: dict[str, str], turns: list[Turn]) -> list[Turn]:
    if index == 6:
        return [
            Turn("INBOUND", "Mình đang tìm hiểu Tư vấn tranh chấp BĐS vì gia đình có vài giấy tờ chưa khớp. Mình muốn biết dịch vụ tiếp nhận thông tin ban đầu thế nào, chưa cần kết luận vụ việc qua chat.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, mức đang ghi cho Tư vấn tranh chấp BĐS là {price_text(offering)}. Anh/chị có thể nêu phần cần làm rõ, còn tình trạng hồ sơ và hướng tư vấn chỉ có thể xem sau khi tiếp nhận thông tin phù hợp."),
            Turn("INBOUND", f"Mức {price_text(offering)} là phí theo giờ tư vấn, đúng không? Mình muốn hiểu rõ trước khi gửi thêm giấy tờ.", "ASK_PRICE", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ đúng, {price_text(offering)} là mức phí dịch vụ đang ghi theo giờ. Những nội dung khác cần được kiểm tra theo hồ sơ, nên em không khẳng định trước qua tin nhắn."),
            Turn("INBOUND", "Mình có thể gửi khung giờ rảnh để bên mình kiểm tra một buổi trao đổi ban đầu không?", "BOOKING", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị gửi vài khung giờ thuận tiện. Em sẽ kiểm tra khả năng sắp xếp trước; chỉ khi có phản hồi phù hợp mới xác nhận buổi trao đổi."),
        ]
    if index == 47:
        return [
            Turn("INBOUND", "Mình đang tìm hiểu Thủ tục sang tên BĐS. Mình muốn biết dịch vụ tiếp nhận hồ sơ theo cách nào để chuẩn bị đúng, chưa cần tư vấn pháp lý ngay trong chat.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, mức đang ghi cho Thủ tục sang tên BĐS là {price_text(offering)}. Phạm vi hồ sơ và nội dung cần kiểm tra sẽ chỉ được xác nhận khi tiếp nhận; em không thể kết luận tình trạng pháp lý qua tin nhắn."),
            Turn("INBOUND", f"Mức {price_text(offering)} là phí dịch vụ đang ghi hay còn khoản nào cần được kiểm tra riêng?", "ASK_PRICE", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, {price_text(offering)} là mức phí dịch vụ đang lưu. Các khoản hoặc điều kiện khác cần được kiểm tra theo hồ sơ, nên em không tự suy diễn thêm."),
            Turn("INBOUND", "Mình có một vài điểm cần hỏi thêm về trường hợp của mình. Bên mình có thể kiểm tra khung giờ trao đổi qua Zalo này không?", "BOOKING", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị có thể gửi vài khung giờ thuận tiện. Em sẽ kiểm tra khả năng sắp xếp trao đổi; chỉ khi kiểm tra được thời gian phù hợp mới có xác nhận lịch."),
        ]
    if index == 48:
        return [
            Turn("INBOUND", "Mình đang tìm hiểu Thẩm định giá BĐS thương mại cho một tài sản thương mại. Hiện mình chỉ muốn hỏi cách tiếp nhận yêu cầu, chưa cần kết quả thẩm định qua chat.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, em có thể kiểm tra cách tiếp nhận ban đầu của Thẩm định giá BĐS thương mại. Giá trị tài sản và kết quả thẩm định không thể xác nhận chỉ từ cuộc chat này."),
            Turn("INBOUND", f"Mức {price_text(offering)} là phí dịch vụ cho lần thực hiện, không phải mức định giá tài sản, đúng không?", "ASK_PRICE", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ đúng, {price_text(offering)} là phí dịch vụ đang ghi, không phải giá trị thẩm định của tài sản. Điều kiện tiếp nhận cần được kiểm tra thêm."),
            Turn("INBOUND", "Mình chưa có đủ thông tin để gửi hồ sơ. Khi xác định rõ mục đích cần thẩm định, mình sẽ liên hệ lại.", "OTHER", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị cứ xác định thêm nhu cầu. Khi liên hệ lại, em sẽ kiểm tra bước phù hợp; hiện chưa có hồ sơ hay yêu cầu thẩm định nào được xác nhận."),
        ]
    if index == 49:
        return [
            Turn("INBOUND", "Mình đang tìm hiểu Tư vấn thuế phí trước bạ. Mình muốn biết phạm vi tiếp nhận để không gửi nhầm thông tin nhạy cảm.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, mức đang ghi cho Tư vấn thuế phí trước bạ là {price_text(offering)}. Phạm vi hồ sơ hoặc tư vấn cần được xác nhận khi tiếp nhận, nên em không kết luận nghĩa vụ cụ thể qua chat."),
            Turn("INBOUND", f"Bạn xác nhận giúp mình mức {price_text(offering)} đang lưu cho dịch vụ này nhé.", "ASK_PRICE", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, {price_text(offering)} là phí dịch vụ đang ghi. Nếu anh/chị cần tìm hiểu thêm, em sẽ kiểm tra điều kiện áp dụng trước khi trả lời."),
            Turn("INBOUND", "Mình chưa cần chốt dịch vụ, chỉ muốn biết bên mình có thể kiểm tra một khung giờ trao đổi khi mình gửi thời gian dự kiến không.", "BOOKING", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị gửi thời gian dự kiến tại đây. Em sẽ kiểm tra khả năng sắp xếp; cuộc hẹn chỉ được xác nhận sau khi có khung giờ phù hợp."),
        ]
    if index == 40:
        return [
            Turn("INBOUND", "Mình có một căn biệt thự đang cân nhắc bán nhưng chưa biết nên chuẩn bị thông tin gì trước. Dịch vụ Môi giới mua bán biệt thự có thể hỗ trợ ở phần nào?", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, Môi giới mua bán biệt thự là dịch vụ theo giao dịch. Anh/chị có thể nêu mục tiêu và thông tin đang có; em sẽ kiểm tra phạm vi phù hợp, không giả định giá bán hay kết quả giao dịch."),
            Turn("INBOUND", f"Mức {price_text(offering)} là phí dịch vụ đang ghi cho một giao dịch, phải không bạn?", "ASK_PRICE", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, {price_text(offering)} là mức phí dịch vụ đang lưu, không phải giá bất động sản. Điều kiện áp dụng cần được kiểm tra theo nội dung tiếp nhận."),
            Turn("INBOUND", "Mình muốn trao đổi ngắn để biết cần chuẩn bị gì. Bên mình có thể kiểm tra khung giờ phù hợp qua Zalo này không?", "BOOKING", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị gửi thời gian dự kiến. Em sẽ kiểm tra khả năng sắp xếp trao đổi; cuộc hẹn chỉ được xác nhận sau khi có khung giờ phù hợp."),
        ]
    if index == 42:
        return [
            Turn("INBOUND", "Mình đang tìm hiểu Dịch vụ làm sổ đỏ sổ hồng và chỉ muốn hỏi cách tiếp nhận hồ sơ ban đầu. Mình chưa cần kết luận pháp lý cho trường hợp của mình qua chat.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, mức đang ghi cho Dịch vụ làm sổ đỏ sổ hồng là {price_text(offering)}. Phạm vi hồ sơ cần được kiểm tra khi tiếp nhận, nên em không thể xác nhận tình trạng pháp lý chỉ từ tin nhắn."),
            Turn("INBOUND", f"Mức {price_text(offering)} là phí dịch vụ đang lưu, còn điều kiện áp dụng sẽ được kiểm tra riêng, đúng không?", "ASK_PRICE", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ đúng, {price_text(offering)} là mức phí dịch vụ đang ghi. Phần điều kiện và giấy tờ cần được đối chiếu theo hồ sơ thực tế."),
            Turn("INBOUND", "Mình muốn hỏi thêm về giấy tờ cần chuẩn bị. Bên mình có thể kiểm tra thời gian trao đổi nếu mình gửi khung giờ dự kiến không?", "BOOKING", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị gửi khung giờ dự kiến tại đây. Em sẽ kiểm tra khả năng sắp xếp; chưa có lịch nào được xác nhận trong cuộc chat này."),
        ]
    return turns


def extend_flow(index: int, offering: dict[str, str], turns: list[Turn]) -> list[Turn]:
    return turns


def rephrase_repeated_turns(turns: list[Turn], index: int, seen: set[str], occurrences: dict[str, int]) -> list[Turn]:
    customer_openings = {
        "ASK_INFO": [
            "Mình muốn hiểu rõ hơn về dịch vụ", "Cho mình hỏi thêm về phạm vi hỗ trợ",
            "Mình đang tham khảo nên muốn xác nhận", "Mình cần biết phần này trước",
            "Mình muốn làm rõ thông tin ban đầu", "Cho mình hỏi theo nhu cầu đang cân nhắc",
            "Mình cần thêm thông tin để trao đổi với gia đình", "Mình muốn kiểm tra phần đang quan tâm",
            "Mình chưa có đủ thông tin nên muốn hỏi", "Mình muốn xác nhận cách bên mình tiếp nhận",
        ],
        "ASK_PRICE": [
            "Mình muốn xác nhận phần chi phí", "Để cân đối ngân sách, mình xin hỏi",
            "Mình cần làm rõ mức phí đang ghi", "Cho mình hỏi lại về khoản chi phí này",
            "Mình đang so chi phí nên muốn xác nhận", "Mình cần hiểu đúng mức phí trước",
            "Mình muốn đối chiếu thông tin giá", "Cho mình xác nhận khoản phí dịch vụ",
            "Mình cần làm rõ chi phí trước khi trao đổi tiếp", "Mình muốn hỏi kỹ hơn về mức đang lưu",
        ],
        "BOOKING": [
            "Mình muốn kiểm tra lịch tư vấn", "Mình muốn hỏi khung giờ phù hợp",
            "Mình muốn biết cách đề nghị một buổi trao đổi", "Mình muốn hẹn trao đổi khi thuận tiện",
            "Mình muốn hỏi khả năng sắp xếp lịch", "Mình muốn kiểm tra trước thời gian có thể trao đổi",
            "Mình muốn gửi đề nghị lịch tư vấn", "Mình muốn hỏi về cách hẹn trao đổi",
            "Mình muốn xem bên mình có nhận đề nghị lịch không", "Mình muốn biết thời gian nào có thể trao đổi",
        ],
        "COMPLAINT": [
            "Mình muốn làm rõ đúng điểm đang phản ánh", "Mình cần câu trả lời thống nhất về việc này",
            "Mình mong bên bạn xử lý theo nội dung đã trao đổi", "Mình muốn nhắc lại phần đang gây khó hiểu",
            "Mình cần làm rõ trước khi cân nhắc thêm", "Mình mong phản hồi sau bám đúng vấn đề này",
            "Mình không muốn phải kể lại từ đầu", "Mình cần phần thông tin này được đối chiếu lại",
            "Mình muốn phản hồi tập trung vào điểm đang vướng", "Mình mong có câu trả lời có căn cứ",
        ],
        "SUPPORT": [
            "Mình muốn biết cách tiếp tục trao đổi", "Mình cần hỏi đầu mối tiếp nhận thông tin",
            "Mình muốn chuẩn bị nội dung trước khi gửi", "Mình cần biết có thể nhắn lại qua đây không",
            "Mình muốn làm rõ cách gửi yêu cầu", "Mình cần biết bước liên hệ phù hợp",
            "Mình muốn hỏi cách tiếp nhận qua Zalo", "Mình cần một đầu mối để hỏi tiếp",
            "Mình muốn kiểm tra kênh trao đổi này", "Mình cần biết cách gửi nội dung cụ thể",
        ],
        "OTHER": [
            "Mình muốn nói rõ quyết định hiện tại", "Mình xin giữ việc trao đổi ở mức tham khảo",
            "Mình muốn cân nhắc thêm trước", "Mình cần thời gian xem lại thông tin",
            "Mình muốn dừng ở phần đã hiểu", "Mình sẽ trao đổi thêm với gia đình",
            "Mình muốn xem kỹ rồi mới phản hồi", "Mình chưa cần thực hiện thêm bước nào",
            "Mình muốn ghi nhận thông tin trước", "Mình sẽ liên hệ lại khi nhu cầu rõ hơn",
        ],
    }
    agent_openings = [
        "em trả lời theo thông tin hiện có", "em xin làm rõ để tránh hiểu nhầm",
        "em chỉ xác nhận phần có thể đối chiếu", "em bám theo câu hỏi anh/chị vừa nêu",
        "em tách rõ thông tin để anh/chị dễ cân nhắc", "em giới hạn ở nội dung có thể xác minh qua chat",
        "em nói gọn phần anh/chị đang hỏi", "em không suy diễn ngoài dữ liệu hiện có",
        "em không giả định nhu cầu chưa được xác nhận", "em làm rõ phần cần biết trước khi trao đổi sâu hơn",
    ]
    varied: list[Turn] = []
    for position, turn in enumerate(turns):
        content = turn.content
        occurrence = occurrences.get(content, 0)
        occurrences[content] = occurrence + 1
        if content in seen:
            if turn.direction == "INBOUND":
                if content == "Mình chưa hỏi lịch xem, chỉ cần biết các thông tin đang được lưu.":
                    content = "Hiện mình chỉ cần tham khảo các thông tin đang lưu, chưa cần hỏi về lịch xem."
                    seen.add(content)
                    varied.append(Turn(turn.direction, content, turn.intent, turn.sentiment))
                    continue
                if content == "Mình chưa cần chốt dịch vụ, chỉ muốn hỏi khả năng hẹn trao đổi để nói rõ nhu cầu.":
                    content = "Mình chưa quyết định dùng dịch vụ; trước mắt chỉ muốn kiểm tra có thể hẹn trao đổi để nói rõ nhu cầu không."
                    seen.add(content)
                    varied.append(Turn(turn.direction, content, turn.intent, turn.sentiment))
                    continue
                choices = customer_openings[turn.intent or "OTHER"]
                choice = (index + occurrence) % len(choices)
                content = f"{choices[choice]}: {content[0].lower()}{content[1:]}"
                while content in seen:
                    choice = (choice + 1) % len(choices)
                    content = f"{choices[choice]}: {turn.content[0].lower()}{turn.content[1:]}"
            else:
                choice = (index + occurrence) % len(agent_openings)
                prefix = agent_openings[choice]
                if content.startswith("Dạ đúng, "):
                    rest = content[len("Dạ đúng, "):]
                    content = f"Dạ, {prefix}: {rest[0].lower()}{rest[1:]}"
                elif content.startswith("Dạ, "):
                    rest = content[len("Dạ, "):]
                    content = f"Dạ, {prefix}: {rest[0].lower()}{rest[1:]}"
                else:
                    content = f"{prefix.capitalize()}: {content[0].lower()}{content[1:]}"
                while content in seen:
                    choice = (choice + 1) % len(agent_openings)
                    prefix = agent_openings[choice]
                    if turn.content.startswith("Dạ đúng, "):
                        rest = turn.content[len("Dạ đúng, "):]
                        content = f"Dạ, {prefix}: {rest[0].lower()}{rest[1:]}"
                    elif turn.content.startswith("Dạ, "):
                        rest = turn.content[len("Dạ, "):]
                        content = f"Dạ, {prefix}: {rest[0].lower()}{rest[1:]}"
                    else:
                        content = f"{prefix.capitalize()}: {turn.content[0].lower()}{turn.content[1:]}"
        seen.add(content)
        varied.append(Turn(turn.direction, content, turn.intent, turn.sentiment))
    return varied


def render_human_review(conversations: list[dict[str, str]], messages: list[dict[str, str]], annotations: list[dict[str, str]]) -> str:
    labels = {row["message_id"]: row for row in annotations}
    by_conversation: dict[str, list[dict[str, str]]] = {}
    for message in messages:
        by_conversation.setdefault(message["conversation_id"], []).append(message)
    lines = ["W13 AI-05 REA pilot - human review", ""]
    for conversation in conversations:
        lines.append(f"[{conversation['external_thread_id']}] {conversation['status']}")
        for message in by_conversation[conversation["id"]]:
            label = labels.get(message["id"])
            suffix = f" [{label['intent']}/{label['sentiment']}]" if label else ""
            lines.append(f"{'KH' if message['direction'] == 'INBOUND' else 'NV'}: {message['content']}{suffix}")
        lines.append("")
    return "\n".join(lines)


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def write_text_crlf(path: Path, content: str, encoding: str) -> None:
    with path.open("w", encoding=encoding, newline="\r\n") as file:
        file.write(content)


def write_text_lf(path: Path, content: str, encoding: str) -> None:
    with path.open("w", encoding=encoding, newline="\n") as file:
        file.write(content)


def write_checksums(output: Path) -> None:
    lines = [f"{hashlib.sha256((output / filename).read_bytes()).hexdigest()}  {filename}" for filename in OUTPUT_FILES]
    write_text_lf(output / "SHA256SUMS.txt", "\n".join(lines) + "\n", "ascii")


def generate(output: Path = DEFAULT_OUTPUT) -> None:
    output.mkdir(parents=True, exist_ok=True)
    account = [{
        "id": CHANNEL_ID, "organization_id": ORG, "channel_type": "ZALO_SIM", "display_name": "POSE Realty Zalo Demo",
        "external_account_id": "pose-realty-zalo-sim", "status": "ACTIVE", "secret_ref": "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET",
        "team_id": TEAM, "default_assignee_user_id": ASSIGNEE, "created_at": iso(START), "updated_at": iso(START),
    }]
    conversations: list[dict[str, str]] = []
    messages: list[dict[str, str]] = []
    annotations: list[dict[str, str]] = []
    seen_contents: set[str] = set()
    content_occurrences: dict[str, int] = {}
    for index, (profile_type, profile) in enumerate(profiles()):
        ordinal = index + 1
        offering = offering_for(index, profile_type, profile)
        workflow, turns = turns_for(index, offering)
        conversation_id = uid("conversation", ordinal)
        started_at = START + timedelta(hours=index * 3, minutes=(index % 4) * 9)
        moments = [started_at + timedelta(minutes=sequence * 7) for sequence in range(len(turns))]
        inbound_times = [when for turn, when in zip(turns, moments) if turn.direction == "INBOUND"]
        conversations.append({
            "id": conversation_id, "organization_id": ORG, "channel_account_id": CHANNEL_ID,
            "external_thread_id": f"zalo-ai05-rea-{ordinal:03d}",
            "lead_id": profile["id"] if profile_type == "LEAD" else "", "customer_id": profile["id"] if profile_type == "CUSTOMER" else "",
            "assigned_user_id": ASSIGNEE, "status": ("OPEN", "PENDING", "CLOSED")[index % 3], "consent_status": "UNKNOWN",
            "last_inbound_at": iso(max(inbound_times)), "last_message_at": iso(moments[-1]), "created_at": iso(started_at), "updated_at": iso(moments[-1]),
        })
        for sequence, (turn, when) in enumerate(zip(turns, moments), start=1):
            message_id = uid("message", ordinal, sequence)
            messages.append({
                "id": message_id, "organization_id": ORG, "conversation_id": conversation_id, "direction": turn.direction,
                "external_message_id": f"zalo-ai05-rea-{ordinal:03d}-{sequence:02d}", "content_type": "TEXT", "content": turn.content,
                "sent_by_user_id": ASSIGNEE if turn.direction == "OUTBOUND" else "", "delivery_status": "SENT" if turn.direction == "OUTBOUND" else "RECEIVED",
                "occurred_at": iso(when), "interaction_id": "", "created_at": iso(when),
            })
            if turn.direction == "INBOUND":
                annotations.append({
                    "id": uid("annotation", message_id), "organization_id": ORG, "conversation_id": conversation_id, "message_id": message_id,
                    "intent": turn.intent or "OTHER", "sentiment": turn.sentiment or "NEUTRAL", "split": "dev" if ordinal <= 40 else "test",
                    "annotator": ANNOTATOR, "annotation_round": "1", "created_at": iso(when), "notes": f"{VERSION}:{workflow}",
                })
    write_csv(output / "channel_accounts.csv", list(account[0]), account)
    write_csv(output / "conversations.csv", list(conversations[0]), conversations)
    write_csv(output / "conversation_messages.csv", list(messages[0]), messages)
    write_csv(output / "round_1_annotations.csv", list(annotations[0]), annotations)
    write_text_crlf(output / "rea_human_review.txt", render_human_review(conversations, messages, annotations), "utf-8")
    write_checksums(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    generate(parser.parse_args().output)
