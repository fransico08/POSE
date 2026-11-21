#!/usr/bin/env python3
"""Generate deterministic, synthetic W13 AI-05 EDU pilot CSV files."""
from __future__ import annotations

import argparse
import csv
import hashlib
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
CANONICAL = ROOT / "database" / "data" / "seed" / "multisector_v2"
DEFAULT_OUTPUT = Path(__file__).resolve().parent

ORG = "0b47be39-3b3c-5195-a4e2-8a29ffbadcf0"
TEAM = "38bb3193-4121-569f-b095-d0ce10f45392"
ASSIGNEE = "8a294c3d-8926-5ab0-aeec-b75765f1cccc"
NAMESPACE = uuid.UUID("15d9edb3-a442-56fd-9fa7-d5dfc3d6a505")
ANNOTATOR = "Van-round-1"
VERSION = "w13-ai05-edu-pilot-v2"
CHANNEL_ID = str(uuid.uuid5(NAMESPACE, "channel-account:zalo-sim-edu-ai05"))
START = datetime(2026, 11, 10, 8, 0, tzinfo=timezone.utc)



@dataclass(frozen=True)
class Turn:
    direction: str
    content: str
    intent: str | None = None
    sentiment: str | None = None


def uid(kind: str, *parts: object) -> str:
    return str(uuid.uuid5(NAMESPACE, ":".join([kind, *(str(part) for part in parts)])))


def iso(value: datetime) -> str:
    return value.strftime("%Y-%m-%dT%H:%M:%SZ")


def csv_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def profiles() -> list[tuple[str, dict[str, str]]]:
    customers = sorted(
        (
            row
            for row in csv_rows(CANONICAL / "customers.csv")
            if row["organization_id"] == ORG
            and row["status"] == "ACTIVE"
            and not row["archived_at"]
        ),
        key=lambda row: row["id"],
    )[:35]
    leads = sorted(
        (
            row
            for row in csv_rows(CANONICAL / "leads.csv")
            if row["organization_id"] == ORG
            and row["status"] in {"NEW", "QUALIFIED"}
            and not row["archived_at"]
        ),
        key=lambda row: row["id"],
    )[:15]
    if len(customers) != 35 or len(leads) != 15:
        raise RuntimeError("Insufficient eligible EDU profiles in canonical V2.")
    return [("CUSTOMER", row) for row in customers] + [("LEAD", row) for row in leads]


def active_offerings() -> dict[str, dict[str, str]]:
    return {
        row["id"]: row
        for row in csv_rows(CANONICAL / "offerings.csv")
        if row["organization_id"] == ORG
        and row["status"] == "ACTIVE"
        and row["code"].startswith("EDU-")
    }


def offering_for(index: int, profile_type: str, profile: dict[str, str]) -> dict[str, str]:
    offerings = active_offerings()
    linked_id = profile["offering_id"] if profile_type == "LEAD" else profile["related_offering_id"]
    if linked_id and linked_id in offerings:
        return offerings[linked_id]
    fallback = sorted(offerings.values(), key=lambda row: row["code"])
    return fallback[index % len(fallback)]


def is_young_learner(offering: dict[str, str]) -> bool:
    return offering["code"].startswith(("EDU-KIDS", "EDU-CAM"))


def workflow_for(index: int, profile_type: str, offering: dict[str, str]) -> str:
    if profile_type == "LEAD" or index < 15:
        if is_young_learner(offering):
            return "admissions_parent"
        options = ["admissions_new", "admissions_returning", "admissions_objection", "admissions_abandoned"]
        return options[index % len(options)]
    return ["academic_support", "onboarding", "schedule_change", "complaint", "renewal_retention"][(index - 15) % 5]


def learner_context(offering: dict[str, str]) -> tuple[str, str, str, str]:
    code = offering["code"]
    if is_young_learner(offering):
        return "con", "phụ huynh", "độ tuổi và nền tảng tiếng Anh của con", "gia đình sắp xếp được sau giờ học"
    if "IELTS" in code:
        return "mình", "người học", "mục tiêu IELTS và kỹ năng đang yếu", "mình cần học ngoài giờ làm"
    if "TOEIC" in code:
        return "mình", "người học", "mốc TOEIC cần đạt và mức hiện tại", "mình chỉ sắp xếp được buổi tối"
    if "BUSINESS" in code or code == "BUSENG":
        return "mình", "người đi làm", "tình huống dùng tiếng Anh trong công việc", "mình cần cân theo lịch họp"
    return "mình", "người học", "mục tiêu giao tiếp và nền tảng hiện có", "mình cần cân theo lịch cá nhân"


def learner_priority(index: int, offering: dict[str, str]) -> str:
    """Return a deterministic, course-appropriate reason for this inquiry."""
    code = offering["code"]
    if is_young_learner(offering):
        priorities = [
            "Gia đình muốn con bớt ngại khi trả lời bằng tiếng Anh.",
            "Phụ huynh ưu tiên nền tảng phù hợp với độ tuổi thay vì chạy theo thành tích.",
            "Gia đình muốn biết cách đồng hành khi con dễ mất tự tin.",
            "Phụ huynh cần một lộ trình vừa sức để con duy trì hứng thú.",
            "Gia đình quan tâm việc con làm quen tiếng Anh một cách tự nhiên.",
        ]
    elif "IELTS" in code:
        priorities = [
            "Mình còn phân vân nên bắt đầu từ kỹ năng nào.",
            "Mình cần cân mục tiêu chứng chỉ với thời gian tự học hiện có.",
            "Mình muốn cải thiện dần, không theo một lộ trình quá sức.",
            "Mình đang chuẩn bị hồ sơ nhưng chưa muốn chốt thời gian thi.",
            "Mình muốn hiểu mức yêu cầu trước khi quyết định học tiếp.",
        ]
    elif "TOEIC" in code:
        priorities = [
            "Mình cần dùng chứng chỉ cho một kế hoạch công việc sắp tới nhưng chưa chốt mốc.",
            "Mình muốn ôn lại nền tảng trước khi đặt mục tiêu điểm số.",
            "Mình lo phần nghe chưa chắc nên không muốn chọn vội.",
            "Mình cần cân việc học với lịch làm ca.",
            "Mình muốn biết lộ trình có phù hợp với mức hiện tại hay không.",
        ]
    else:
        priorities = [
            "Mình muốn dùng tiếng Anh tự tin hơn trong công việc hằng ngày.",
            "Mình đã quên nhiều kiến thức cơ bản và muốn bắt đầu vừa sức.",
            "Mình cần ưu tiên phản xạ giao tiếp nhưng không muốn học dồn.",
            "Mình muốn chọn hướng học phù hợp trước khi sắp xếp thời gian.",
            "Mình quan tâm khả năng áp dụng thực tế hơn là học theo phong trào.",
        ]
    return priorities[index % len(priorities)]


def customer_subject(offering: dict[str, str]) -> str:
    return "Phụ huynh" if is_young_learner(offering) else "Mình"


def case_reference(index: int, offering: dict[str, str]) -> str:
    """Return a concrete customer circumstance for a deterministic case."""
    code = offering["code"]
    if is_young_learner(offering):
        details = [
            "việc con còn ngại trả lời trước người lạ",
            "thời gian gia đình có thể đưa đón sau giờ học",
            "mong muốn để con làm quen dần thay vì bị áp lực",
            "việc con dễ bỏ cuộc khi gặp bài mới",
            "cách gia đình theo dõi sự tiến bộ mà không so sánh con với bạn",
            "nỗi lo con chưa dám nói thành câu dài",
            "mong muốn giữ hứng thú học cho con trong giai đoạn đầu",
            "việc con cần thêm thời gian để thích nghi với lớp mới",
            "thời điểm gia đình đang sắp xếp lại lịch đưa đón",
            "mong muốn chọn một hướng học vừa sức cho con",
        ]
    elif "IELTS" in code:
        details = [
            "hồ sơ học tập đang chuẩn bị nhưng chưa chốt ngày thi",
            "kỹ năng nghe còn thiếu tự tin khi tự luyện",
            "mục tiêu cải thiện viết trước khi cân nhắc thi",
            "thời gian tự học bị chia nhỏ bởi lịch làm thêm",
            "việc chưa biết nên ưu tiên nền tảng hay luyện đề",
            "mong muốn học đều thay vì chạy nước rút",
            "kế hoạch nộp hồ sơ trong năm tới",
            "nỗi lo chọn lộ trình vượt quá mức hiện tại",
            "nhu cầu cân đối giữa tiếng Anh và việc học ở trường",
            "mong muốn hiểu rõ yêu cầu trước khi đăng ký",
        ]
    elif "TOEIC" in code:
        details = [
            "một cơ hội công việc có thể cần chứng chỉ tiếng Anh",
            "lịch làm ca khiến thời gian ôn không đều",
            "phần nghe còn là trở ngại lớn nhất",
            "mục tiêu ôn lại nền tảng trước khi đặt mốc điểm",
            "việc phải cân tiếng Anh với công việc hiện tại",
            "mong muốn dùng kết quả học cho kế hoạch nghề nghiệp",
            "nỗi lo học không theo kịp khi đổi ca",
            "nhu cầu biết lộ trình có vừa sức hay không",
            "thời gian ôn chỉ có vào các buổi tối",
            "việc chưa muốn chốt mục tiêu điểm quá sớm",
        ]
    else:
        details = [
            "nhu cầu dùng tiếng Anh trong các cuộc họp ngắn",
            "việc đã quên nhiều kiến thức cơ bản",
            "mong muốn nói tự nhiên hơn khi làm việc với khách nước ngoài",
            "lịch họp thay đổi khiến việc học khó đều",
            "nhu cầu luyện phản xạ thay vì học dồn lý thuyết",
            "việc phải cân thời gian học với gia đình",
            "mong muốn bắt đầu chậm mà chắc sau thời gian gián đoạn",
            "nỗi lo chọn lớp cao hơn mức hiện tại",
            "nhu cầu dùng tiếng Anh cho tình huống công việc cụ thể",
            "mong muốn có hướng học thực tế thay vì theo phong trào",
        ]
    return details[index % len(details)]


def rewrite_repeated_turns(
    turns: list[Turn],
    index: int,
    offering: dict[str, str],
    workflow: str,
    seen_contents: set[str],
) -> list[Turn]:
    """Replace repeated substantive turns with workflow-specific dialogue."""
    varied: list[Turn] = []
    course = offering["name"]
    reference = case_reference(index, offering)
    for sequence, turn in enumerate(turns, start=1):
        content = turn.content
        if content in seen_contents:
            conversation_moment = [
                "trước khi quyết định",
                "khi sắp xếp tiếp",
                "để chủ động thời gian",
                "sau khi cân nhắc thêm",
                "trước khi chốt",
            ][(sequence - 1) % 5]
            staff_actions = [
                "Em sẽ kiểm tra phần thông tin đang áp dụng",
                "Em sẽ đối chiếu nhu cầu anh/chị vừa nêu với thông tin hiện có",
                "Em sẽ xem lại nội dung cần xác nhận",
                "Em sẽ kiểm tra lựa chọn phù hợp với tình huống này",
                "Em sẽ tổng hợp phần đã xác minh",
            ]
            staff_action = staff_actions[(sequence - 1) % len(staff_actions)]
            if turn.direction == "INBOUND":
                inbound_rewrites = {
                    "ASK_INFO": f"{conversation_moment.capitalize()}, mình muốn hỏi kỹ về {course}, nhất là {reference}.",
                    "ASK_PRICE": f"{conversation_moment.capitalize()}, mình muốn biết học phí của {course} có phù hợp với {reference} không.",
                    "BOOKING": f"Vì {reference}, mình muốn hỏi cách đặt lịch trao đổi phù hợp {conversation_moment}.",
                    "PURCHASE_READY": f"Nếu thông tin về {reference} phù hợp, mình sẽ cân nhắc đăng ký {course} {conversation_moment}.",
                    "COMPLAINT": f"Việc {reference} làm mình chưa yên tâm; {conversation_moment} mình cần được giải thích rõ về {course}.",
                    "SUPPORT": f"Mình cần gợi ý xử lý việc {reference} khi theo dõi {course}, {conversation_moment}.",
                    "OTHER": f"Vì {reference}, mình cần sắp xếp lại rồi sẽ trao đổi tiếp về {course} {conversation_moment}.",
                }
                content = inbound_rewrites[turn.intent or "OTHER"]
            else:
                if workflow == "complaint":
                    content = f"Dạ, em hiểu vì {reference} khiến anh/chị chưa yên tâm. {staff_action} của {course} và chỉ phản hồi phần đã xác minh."
                elif workflow == "schedule_change":
                    content = f"Dạ, với việc {reference}, {staff_action.lower()} của {course} rồi báo lại lựa chọn có thể xác nhận."
                elif workflow == "academic_support":
                    content = f"Dạ, em hiểu băn khoăn về {reference}. {staff_action} trong {course}; em không tự kết luận khi chưa có thông tin xác minh."
                elif workflow == "onboarding":
                    content = f"Dạ, vì {reference}, {staff_action.lower()} cho {course} rồi gửi phần cần thiết để anh/chị chủ động chuẩn bị."
                elif workflow == "renewal_retention":
                    content = f"Dạ, {staff_action.lower()} của {course} liên quan đến {reference}, rồi gửi anh/chị phần đã xác minh để tự cân nhắc."
                else:
                    content = f"Dạ, {staff_action.lower()} của {course} liên quan đến {reference}, rồi trả lời đúng phần anh/chị cần biết."
        seen_contents.add(content)
        varied.append(Turn(turn.direction, content, turn.intent, turn.sentiment))
    return varied


def raw_scenario(index: int, profile_type: str, offering: dict[str, str]) -> tuple[str, list[Turn]]:
    course = offering["name"]
    learner, contact_role, discovery, availability = learner_context(offering)
    priority = learner_priority(index, offering)
    subject = customer_subject(offering)
    customer_decision_maker = "gia đình" if is_young_learner(offering) else "mình"
    staff_decision_maker = "gia đình" if is_young_learner(offering) else "anh/chị"
    variant = index % 4
    workflow = workflow_for(index, profile_type, offering)
    if workflow == "admissions_new":
        return workflow, [
            Turn("INBOUND", f"Mình đang tìm hiểu {course}. {priority} Bên mình tư vấn giúp {discovery} được không?", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ được ạ. Anh/chị chia sẻ thêm về {discovery} và khung giờ thuận tiện để em kiểm tra thông tin phù hợp về {course} nhé."),
            Turn("INBOUND", f"{availability}; mình muốn biết có cần đánh giá đầu vào không.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", "Em sẽ kiểm tra lớp đang mở và quy trình đánh giá đầu vào nếu chương trình có áp dụng, rồi phản hồi lựa chọn phù hợp."),
            Turn("INBOUND", "Cho mình xin học phí và các bước đăng ký sau khi kiểm tra nhé.", "ASK_PRICE", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ em sẽ gửi thông tin học phí và đăng ký đã được xác minh theo chương trình cụ thể; em không báo trước khi chưa kiểm tra."),
        ]
    if workflow == "admissions_parent":
        parent_situations = [
            "Gia đình muốn hiểu cách chọn nhịp học trước khi sắp xếp thời gian.",
            "Phụ huynh đang cân giữa việc để con làm quen dần và mục tiêu học dài hơn.",
            "Gia đình muốn biết nên lưu ý điều gì khi con chưa tự tin dùng tiếng Anh.",
            "Phụ huynh ưu tiên môi trường phù hợp để con không bị áp lực ngay từ đầu.",
        ]
        parent_follow_ups = [
            "Gia đình mong có hướng tham khảo rõ trước khi quyết định.",
            "Phụ huynh muốn cân kỹ vì con dễ mất hứng nếu nhịp học không phù hợp.",
            "Gia đình cần chủ động sắp xếp, nên muốn hiểu thông tin trước.",
            "Phụ huynh không muốn ép con theo một lựa chọn chưa phù hợp.",
        ]
        return workflow, [
            Turn("INBOUND", f"Mình là phụ huynh, muốn tìm hiểu {course} cho con. {parent_situations[variant]} {priority} Mình cần biết chương trình có phù hợp với độ tuổi của cháu không.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, em hiểu điều gia đình đang quan tâm. Anh/chị cho em biết độ tuổi, nền tảng tiếng Anh của con và điều gia đình mong cải thiện để em kiểm tra thông tin phù hợp về {course}."),
            Turn("INBOUND", f"{availability}; {parent_follow_ups[variant]}", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", f"Em sẽ đối chiếu mục tiêu và lịch của gia đình với thông tin hiện có về {course}. Em không cam kết kết quả học tập khi chưa có đánh giá phù hợp."),
            Turn("INBOUND", f"Phụ huynh cần biết học phí của {course} trước khi sắp xếp.", "ASK_PRICE", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, em sẽ kiểm tra mức phí và điều kiện hiện hành của {course} rồi phản hồi; em không tự báo ưu đãi khi chưa xác minh."),
        ]
    if workflow == "admissions_returning":
        return workflow, [
            Turn("INBOUND", f"Mình quay lại tìm hiểu {course}. {priority} Không biết lộ trình này phù hợp với mục tiêu hiện tại không?", "ASK_INFO", "POSITIVE"),
            Turn("OUTBOUND", "Dạ, anh/chị cho em biết mục tiêu hiện tại và phần muốn cải thiện để bên em kiểm tra thông tin phù hợp."),
            Turn("INBOUND", f"Mình muốn theo {course} nhưng {availability}.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", "Em sẽ kiểm tra lớp, lịch và điều kiện đầu vào thực tế trước khi hướng dẫn các bước đăng ký."),
            Turn("INBOUND", "Nếu phù hợp thì mình muốn đăng ký tiếp.", "PURCHASE_READY", "POSITIVE"),
            Turn("OUTBOUND", "Dạ, em sẽ chuyển nhu cầu để tư vấn viên phản hồi thông tin đã xác minh; quyết định vẫn do anh/chị chủ động."),
        ]
    if workflow == "admissions_objection":
        return workflow, [
            Turn("INBOUND", f"Mình quan tâm {course} nhưng lo học phí và sợ không theo được. {priority}", "ASK_PRICE", "NEGATIVE"),
            Turn("OUTBOUND", f"Dạ, em hiểu băn khoăn của anh/chị. Anh/chị cho em biết {discovery} và lịch có thể học để em kiểm tra lựa chọn phù hợp."),
            Turn("INBOUND", f"{availability}, mình lo không theo kịp nếu nhịp học không phù hợp.", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", "Em hiểu giới hạn về lịch và việc anh/chị muốn cân nhắc kỹ. Em sẽ kiểm tra thông tin thực tế, không hứa rằng chương trình nào cũng phù hợp."),
            Turn("INBOUND", "Có thông tin rõ mình mới quyết định.", "OTHER", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, em sẽ gửi phần đã xác minh để anh/chị tự cân nhắc, không thúc đăng ký."),
        ]
    if workflow == "admissions_abandoned":
        return workflow, [
            Turn("INBOUND", f"Mình muốn hỏi {course}, nhưng chưa chắc mức hiện tại của mình có phù hợp không. {priority}", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", f"Dạ, anh/chị chia sẻ giúp em {discovery} và lịch rảnh; em sẽ kiểm tra thông tin đúng với chương trình anh/chị đang quan tâm."),
            Turn("INBOUND", "Mình đang bận, để hôm khác hỏi tiếp nhé.", "OTHER", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, khi thuận tiện anh/chị nhắn lại. Em không tự đặt lịch hay giữ chỗ khi chưa có xác nhận."),
        ]
    if workflow == "onboarding":
        onboarding_openers = [
            f"{subject} muốn chuẩn bị trước khi bắt đầu {course}: xem hướng dẫn và tài liệu ở đâu?",
            f"{subject} muốn biết cần chuẩn bị gì trước buổi đầu của {course}, để không bị lúng túng.",
            f"{subject} đang cân nhắc {course} và muốn biết khi cần hỗ trợ tài liệu thì nên nhắn qua kênh nào.",
            f"{subject} muốn hỏi cách nhận thông tin ban đầu của {course}, nhất là khi cần xem lại hướng dẫn.",
        ]
        onboarding_follow_ups = [
            "Nếu gần buổi đầu mà chưa thấy tài liệu thì nên báo thế nào ạ?",
            "Nếu mình chưa hiểu hướng dẫn, có thể mô tả vướng mắc ngay trong chat này không?",
            "Mình cần hỏi trước để chủ động chuẩn bị, không muốn đến lúc bắt đầu mới lúng túng.",
            "Nếu chưa tìm thấy thông tin phù hợp thì cần gửi những gì để được hỗ trợ đúng?",
        ]
        return workflow, [
            Turn("INBOUND", f"{onboarding_openers[variant]} {priority}", "SUPPORT", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, em có thể kiểm tra hướng dẫn hiện hành theo đúng chương trình. Anh/chị đang cần ưu tiên phần tài liệu, thông tin buổi đầu hay cách liên hệ khi gặp vướng mắc?"),
            Turn("INBOUND", onboarding_follow_ups[variant], "SUPPORT", "NEUTRAL"),
            Turn("OUTBOUND", "Anh/chị có thể nhắn tại đây kèm tên chương trình và phần đang thiếu; bên em sẽ kiểm tra rồi hướng dẫn bước tiếp theo. Không cần gửi mật khẩu trong chat."),
            Turn("INBOUND", "Gia đình muốn hỏi trước để con chuẩn bị tâm lý tốt hơn." if is_young_learner(offering) else "Mình muốn chuẩn bị trước thay vì đợi đến lúc bắt đầu.", "OTHER", "POSITIVE"),
            Turn("OUTBOUND", "Dạ, em hiểu. Em sẽ gửi lại phần hướng dẫn đã kiểm tra, không tự khẳng định thông tin khi chưa xác minh."),
        ]
    if workflow == "academic_support" and is_young_learner(offering):
        parent_concerns = [
            "Con hơi nhút nhát khi trả lời bằng tiếng Anh.",
            "Gia đình lo con dễ nản khi gặp phần khó.",
            "Con thường cần thêm thời gian mới quen một hoạt động mới.",
            "Gia đình muốn biết cách hỗ trợ con mà không tạo áp lực.",
        ]
        return workflow, [
            Turn("INBOUND", f"Mình đang cân nhắc {course} cho con. {parent_concerns[variant]} Nếu sau này tiến bộ chậm thì phụ huynh có thể trao đổi và xin hỗ trợ như thế nào?", "SUPPORT", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị có thể nêu rõ điều gia đình đang lo và tình huống cần hỗ trợ. Em sẽ kiểm tra kênh trao đổi phù hợp trước khi hướng dẫn."),
            Turn("INBOUND", "Gia đình không cần nhận xét chung; khi cần trao đổi về tiến độ của con thì nên gửi thông tin gì để bên em hiểu đúng vấn đề?", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị có thể gửi nội dung đang băn khoăn và tên chương trình. Em sẽ hướng dẫn kênh trao đổi phù hợp; em không tự đưa ra đánh giá về tiến độ, giáo viên hay điểm số khi chưa có thông tin được xác minh."),
            Turn("INBOUND", "Nếu cách trao đổi rõ ràng thì gia đình sẽ cân nhắc đăng ký cho con theo học.", "PURCHASE_READY", "POSITIVE"),
            Turn("OUTBOUND", "Dạ, em hiểu. Em sẽ gửi hướng dẫn đã kiểm tra để gia đình chủ động cân nhắc."),
        ]
    if workflow == "academic_support":
        adult_support_topics = [
            "mình hay bị chững lại khi tự ôn phần nghe",
            "mình chưa biết nên hỏi thế nào khi vướng bài viết",
            "mình lo học không đều vì lịch làm việc thay đổi",
            "mình muốn biết cách xin hướng dẫn khi không theo kịp nhịp tự học",
        ]
        return workflow, [
            Turn("INBOUND", f"Mình đang tìm hiểu {course}; {adult_support_topics[variant]}. Nếu gặp tình huống đó thì nên nhờ hỗ trợ như thế nào?", "SUPPORT", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị có thể nêu phần đang vướng và những gì đã thử; bên em sẽ kiểm tra cách chuyển đúng câu hỏi để nhận hướng dẫn phù hợp."),
            Turn("INBOUND", "Khi cần trao đổi về cách học hoặc tiến độ, mình nên mô tả vấn đề thế nào để tránh phải hỏi lại nhiều lần?", "ASK_INFO", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, bên em sẽ kiểm tra kênh trao đổi phù hợp. Em không tự nêu nhận xét về tiến độ, giáo viên hay điểm số khi chưa có thông tin được xác minh."),
            Turn("INBOUND", "Có hướng dẫn rõ thì mình sẽ cân nhắc đăng ký chương trình.", "PURCHASE_READY", "POSITIVE"),
            Turn("OUTBOUND", "Dạ, em sẽ gửi phần hướng dẫn đã kiểm tra để anh/chị chủ động cân nhắc."),
        ]
    if workflow == "schedule_change":
        return workflow, [
            Turn("INBOUND", f"{subject} muốn hỏi trước: nếu lịch thay đổi thì {course} hỗ trợ đổi buổi thế nào? {priority}", "SUPPORT", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, anh/chị cho em biết khung giờ dự kiến và lựa chọn thay thế để em kiểm tra điều kiện đổi buổi theo chương trình."),
            Turn("INBOUND", "Nếu chỉ sắp xếp được cuối tuần thì có thể đề nghị kiểm tra lịch không?", "BOOKING", "NEUTRAL"),
            Turn("OUTBOUND", "Em đã cập nhật mong muốn cuối tuần. Em chưa thể xác nhận còn chỗ hay điều kiện đổi buổi khi chưa kiểm tra lớp cụ thể."),
            Turn("INBOUND", "Mình cần biết quy trình đổi buổi trước để chủ động sắp xếp.", "BOOKING", "NEUTRAL"),
            Turn("OUTBOUND", "Dạ, em hiểu anh/chị cần chủ động sắp xếp. Em sẽ phản hồi khi có thông tin được xác nhận."),
        ]
    if workflow == "complaint":
        complaint_contexts = [
            "Mình cần làm rõ vì đang so sánh thông tin trước khi quyết định.",
            "Mình đã hỏi lại nhưng câu trả lời trước vẫn chưa giải quyết được băn khoăn.",
            "Mình không muốn nhận thêm thông tin chung chung.",
            "Việc này làm mình dè dặt khi cân nhắc chương trình.",
        ]
        return workflow, [
            Turn("INBOUND", f"Mình đã hỏi về {course} nhưng thông tin được tư vấn không khớp với điều mình hiểu về chương trình, muốn được giải thích. {complaint_contexts[variant]}", "COMPLAINT", "NEGATIVE"),
            Turn("OUTBOUND", "Dạ, em tiếc vì thông tin chưa rõ ràng. Anh/chị cho em biết phần nào chưa khớp để em kiểm tra nguồn thông tin trước khi phản hồi."),
            Turn("INBOUND", "Mình không cần hứa hẹn; chỉ cần biết nội dung sẽ được kiểm tra thế nào và ai là đầu mối phản hồi.", "COMPLAINT", "NEGATIVE"),
            Turn("OUTBOUND", "Dạ, em hiểu anh/chị cần một đầu mối rõ ràng. Em sẽ kiểm tra người phụ trách phù hợp; hiện em chưa thể cam kết thời hạn khi chưa có xác nhận."),
            Turn("INBOUND", "Nếu được giải thích thỏa đáng mình vẫn cân nhắc học tiếp.", "PURCHASE_READY", "NEUTRAL"),
            Turn("OUTBOUND", "Cảm ơn anh/chị vẫn cân nhắc. Trước hết em sẽ làm rõ phản ánh và chỉ cập nhật thông tin có căn cứ."),
        ]
    retention_needs = [
        "Mình muốn tiếp tục theo mục tiêu đã đặt nhưng chưa muốn quyết định vội.",
        "Mình đang cân giữa việc học tiếp và tạm hoãn vì lịch cá nhân thay đổi.",
        "Mình muốn hiểu lựa chọn phù hợp trước khi dành thêm thời gian cho việc học.",
        "Mình chưa chắc nên duy trì cùng hướng hay chuyển trọng tâm kỹ năng.",
    ]
    return workflow, [
        Turn("INBOUND", f"Mình đang cân nhắc {course}. {retention_needs[variant]} Nếu sau này cần học tiếp thì có thể tham khảo theo hướng nào?", "ASK_INFO", "NEUTRAL"),
        Turn("OUTBOUND", "Dạ, anh/chị muốn ưu tiên kỹ năng nào và lịch nào còn khả thi? Em sẽ kiểm tra các lựa chọn hiện có để anh/chị tham khảo."),
        Turn("INBOUND", f"{subject} muốn biết lựa chọn nào hợp với {learner}, nhưng không muốn quyết định khi thông tin chưa rõ.", "ASK_INFO", "NEUTRAL"),
        Turn("OUTBOUND", f"Em hiểu {staff_decision_maker} muốn cân nhắc kỹ. Em sẽ kiểm tra chương trình, học phí và điều kiện hiện hành trước khi gửi thông tin."),
        Turn("INBOUND", f"Nếu có lựa chọn phù hợp thì {customer_decision_maker} sẽ cân nhắc đăng ký tiếp.", "PURCHASE_READY", "POSITIVE"),
        Turn("OUTBOUND", "Dạ, em sẽ gửi thông tin đã xác minh để anh/chị chủ động quyết định, không cần chốt ngay trong cuộc trò chuyện này."),
    ]




# Human-reviewed scenario corrections; preserve original inbound label sequence.
MANUAL_DIALOGUE_OVERRIDES = {15: ['Mình có bé 5 tuổi, khá ngại nói tiếng Anh với người lạ. Trung tâm có chương trình Mầm non 4-6 tuổi '
      'phù hợp không ạ?',
      'Dạ, chương trình Mầm non 4-6 tuổi đúng nhóm tuổi của bé. Để tư vấn sát hơn, chị cho em hỏi bé đã từng '
      'làm quen tiếng Anh ở lớp hay ở nhà chưa ạ?',
      'Bé chỉ biết vài từ qua bài hát thôi, cứ bị hỏi là im. Mình sợ vào lớp con bị áp lực.',
      'Dạ, với bé đang ngại nói, mình nên ưu tiên cảm giác thoải mái và việc con dám tham gia trước, không '
      'vội so với các bạn. Em có thể kiểm tra thông tin chương trình và cách trung tâm trao đổi tiến độ với '
      'phụ huynh để chị cân nhắc.',
      'Vậy chị cho mình xin học phí và thông tin cách theo dõi tiến bộ của bé nhé.',
      'Dạ, em sẽ kiểm tra học phí hiện hành của Mầm non 4-6 tuổi và thông tin trao đổi với phụ huynh, rồi '
      'gửi lại qua Zalo này. Hai nội dung đó em sẽ ghi riêng để chị dễ xem ạ.'],
 23: ['Mình đang tìm hiểu TOEIC Starter 450-600 nhưng làm ca luân phiên. Nếu trùng lịch học thì bên mình có '
      'hỗ trợ đổi buổi không?',
      'Dạ, em hiểu lịch làm ca khó cố định. Việc đổi buổi còn tùy quy định và lớp đang có, nên em chưa dám '
      'xác nhận ngay. Anh/chị thường biết lịch ca trước bao lâu ạ?',
      'Thường trước một tuần. Mình muốn hỏi có thể báo đổi lịch qua Zalo này không?',
      'Anh/chị có thể nhắn lịch bị trùng và buổi muốn đổi tại đây để bên em kiểm tra phương án phù hợp. Em '
      'sẽ xác nhận lại kênh và điều kiện đổi buổi trước khi hướng dẫn cụ thể.',
      'Vậy nhờ bạn kiểm tra giúp, mình cần biết trước khi quyết định đăng ký.',
      'Dạ được ạ. Em sẽ hỏi rõ quy định đổi buổi của TOEIC Starter 450-600 và phản hồi tại cuộc chat này. '
      'Anh/chị chưa cần chốt đăng ký khi chưa rõ phần lịch.'],
 24: ['Hôm trước mình hỏi Giao tiếp Nâng cao B2, một bạn nói một kiểu, hôm nay lại được tư vấn khác. Mình '
      'muốn biết thông tin nào mới đúng.',
      'Dạ, em xin lỗi vì hai lần tư vấn chưa thống nhất, như vậy rất khó để anh/chị quyết định. Anh/chị giúp '
      'em biết hai thông tin đang khác nhau ở điểm nào để em đối chiếu chính xác nhé.',
      'Một bên nói về nội dung học, bên kia lại giải thích khác. Mình không muốn phải kể lại nhiều lần nữa.',
      'Dạ, em hiểu. Anh/chị có thể gửi lại hai đoạn tin nhắn hoặc tóm tắt đúng hai điểm khác nhau. Em sẽ ghi '
      'rõ nội dung cần xác minh, chuyển cho đầu mối phụ trách chương trình và cập nhật lại ngay trong cuộc '
      'chat này; anh/chị không phải mở yêu cầu mới.',
      'Nếu được giải thích rõ ràng thì mình vẫn cân nhắc đăng ký, nhưng cần câu trả lời thống nhất trước.',
      'Dạ, em hiểu. Em sẽ ưu tiên làm rõ phần thông tin mâu thuẫn trước, sau đó mới gửi nội dung chương '
      'trình đã xác nhận để anh/chị tự cân nhắc. Em chưa hẹn thời gian cụ thể khi chưa có xác nhận từ bộ '
      'phận phụ trách.'],
 41: ['Mình đang tìm hiểu IELTS Advanced 6.0-6.5. Reading của mình ổn hơn Writing, không biết nên bắt đầu '
      'cải thiện kỹ năng nào trước?',
      'Dạ, nếu Writing đang yếu hơn thì mình nên làm rõ khó khăn cụ thể trước khi chọn hướng học. Bạn thường '
      'vướng ở việc lên ý, triển khai bài hay ngữ pháp ạ? Em sẽ đối chiếu mục tiêu với nội dung IELTS '
      'Advanced 6.0-6.5.',
      'Mình hay bí ý ở Task 2, viết xong cũng không biết bài thiếu gì. Mình chưa chốt ngày thi.',
      'Vậy nhu cầu trước mắt là luyện cách lập luận và biết điểm cần sửa trong bài viết, chưa phải chạy theo '
      'lịch thi. Em sẽ kiểm tra chương trình có nội dung hỗ trợ phù hợp đến đâu, rồi tư vấn dựa trên thông '
      'tin được xác nhận.',
      'Mình muốn biết học phí của IELTS Advanced 6.0-6.5 trước để cân đối ngân sách.',
      'Dạ, em sẽ kiểm tra mức học phí đang áp dụng và gửi lại qua đây. Nếu bạn muốn, em cũng có thể hỏi thêm '
      'cách chương trình hỗ trợ Writing để bạn cân nhắc cả nội dung lẫn chi phí.']}
MANUAL_DIALOGUE_OVERRIDES.update({
  28: ['Mình làm công việc họp phát sinh nên đang tìm hiểu Phát âm & Phản xạ A1. Nếu có lần trùng lịch, mình '
       'cần hỏi trước cách xin đổi buổi.',
       'Dạ, anh/chị cho em biết thường biết lịch họp trước bao lâu và khung giờ có thể thay thế. Em sẽ kiểm tra '
       'điều kiện áp dụng của chương trình, không tự hứa đổi được buổi nào cũng được.',
       'Có khi chỉ báo trước một hai ngày. Nếu tuần đó mình chỉ rảnh tối hoặc cuối tuần thì có thể nhắn hỏi tại '
       'đây không?',
       'Anh/chị có thể nhắn thời điểm bị trùng và khung giờ còn rảnh. Em sẽ chuyển đúng nhu cầu để kiểm tra, rồi '
       'nói rõ phần nào có thể sắp xếp được.',
       'Vậy mình muốn biết cách gửi yêu cầu trước khi quyết định đăng ký, để không bị động khi lịch họp đổi.',
       'Dạ, em sẽ hỏi cách tiếp nhận yêu cầu đổi buổi của Phát âm & Phản xạ A1 và gửi lại tại cuộc chat này. '
       'Anh/chị chưa cần chốt khi phần lịch chưa rõ.'],
  29: ['Mình đang xem Cambridge KET A2 cho con nhưng hai lần tư vấn nói khác nhau về cách gia đình theo dõi việc '
       'học. Mình muốn biết thông tin nào là đúng.',
       'Dạ, em xin lỗi vì thông tin chưa thống nhất. Anh/chị cho em biết hai điểm đang khác nhau để em đối chiếu '
       'đúng nội dung của Cambridge KET A2 nhé.',
       'Một bạn nói phụ huynh sẽ nhận hướng dẫn riêng, bạn khác lại bảo phải tự hỏi từng lần. Mình cần biết rõ '
       'trước khi đăng ký cho con.',
       'Dạ, anh/chị có thể gửi lại hai phần đã được tư vấn. Em sẽ làm rõ cách trao đổi thông tin với gia đình và '
       'cập nhật một câu trả lời thống nhất tại đây; em không tự đặt ra quy trình khi chưa được xác nhận.',
       'Nếu làm rõ được phần này thì mình vẫn cân nhắc Cambridge KET A2 cho con.',
       'Dạ, em sẽ ưu tiên kiểm tra đúng điểm anh/chị hỏi rồi gửi lại phần đã xác minh để gia đình tự cân nhắc.'],
  33: ['Mình đang tìm hiểu IELTS Foundation 4.0-4.5 nhưng lịch học thêm thay đổi khá nhiều. Nếu trùng lịch thì '
       'mình cần biết có thể hỏi phương án khác thế nào.',
       'Dạ, anh/chị cho em biết thời điểm thường bị trùng và khung giờ còn học được. Em sẽ kiểm tra lựa chọn '
       'theo IELTS Foundation 4.0-4.5, chưa xác nhận lịch khi chưa có thông tin cụ thể.',
       'Mình thường rảnh buổi tối, nhưng có tuần phải học thêm. Mình muốn hỏi trước để không đăng ký rồi mới '
       'không theo được.',
       'Dạ, em hiểu. Anh/chị có thể nêu lịch dự kiến khi cần đổi; em sẽ hỏi điều kiện áp dụng và phản hồi phần '
       'có thể xác minh, thay vì hứa sẵn một phương án.',
       'Vậy nhờ bạn cho mình biết cách trao đổi về lịch trước khi mình quyết định đăng ký.',
       'Dạ, em sẽ gửi lại hướng dẫn tiếp nhận yêu cầu liên quan đến lịch của IELTS Foundation 4.0-4.5 để anh/chị '
       'cân nhắc chủ động.'],
  34: ['Mình hỏi Phát âm & Phản xạ A1 vì muốn giao tiếp tự tin hơn, nhưng lần trước được nói theo một hướng, lần '
       'này lại khác. Mình muốn làm rõ trước khi học.',
       'Dạ, em xin lỗi vì điều đó khiến anh/chị khó quyết định. Anh/chị cho em biết phần nào của Phát âm & Phản '
       'xạ A1 đang được tư vấn khác nhau để em kiểm tra đúng trọng tâm nhé.',
       'Mình chỉ cần biết chương trình có phù hợp với người hay bận họp không; không muốn nghe chung chung nữa.',
       'Dạ, em sẽ làm rõ phần thông tin liên quan đến lịch của người đi làm và nói rõ đâu là nội dung đã được '
       'xác nhận. Em không tự suy ra lịch học hay quyền đổi buổi khi chưa có căn cứ.',
       'Nếu câu trả lời rõ ràng thì mình vẫn cân nhắc đăng ký Phát âm & Phản xạ A1.',
       'Dạ, em sẽ tổng hợp đúng phần anh/chị cần biết và gửi lại tại đây để anh/chị tự quyết định.'],
})

# The manually reviewed pre-enrollment request in EDU-023 asks for information,
# rather than scheduling an appointment or class change.
MANUAL_LABEL_OVERRIDES = {
    (23, 5): ("ASK_INFO", "NEUTRAL"),
}

def scenario(index: int, profile_type: str, offering: dict[str, str]) -> tuple[str, list[Turn]]:
    workflow, turns = raw_scenario(index, profile_type, offering)
    replacement = MANUAL_DIALOGUE_OVERRIDES.get(index + 1)
    if replacement is not None:
        if len(replacement) != len(turns):
            raise ValueError("Human-reviewed dialogue must preserve turn count")
        turns = [
            Turn(original.direction, content, original.intent, original.sentiment)
            for original, content in zip(turns, replacement)
        ]
    for (conversation_number, message_number), (intent, sentiment) in MANUAL_LABEL_OVERRIDES.items():
        if conversation_number == index + 1:
            position = message_number - 1
            original = turns[position]
            turns[position] = Turn(original.direction, original.content, intent, sentiment)
    return workflow, turns


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def write_checksums(output: Path, filenames: list[str]) -> None:
    lines = []
    for filename in filenames:
        digest = hashlib.sha256((output / filename).read_bytes()).hexdigest()
        lines.append(f"{digest}  {filename}\n")
    with (output / "SHA256SUMS.txt").open("w", encoding="ascii", newline="\n") as file:
        file.writelines(lines)


def render_human_review(
    conversations: list[dict[str, str]],
    messages: list[dict[str, str]],
    annotations: list[dict[str, str]],
) -> str:
    """Render the review document directly from the generated conversation rows."""
    labels = {row["message_id"]: row for row in annotations}
    by_conversation: dict[str, list[dict[str, str]]] = {}
    for message in messages:
        by_conversation.setdefault(message["conversation_id"], []).append(message)

    blocks: list[str] = []
    for conversation in conversations:
        lines = ["=" * 50, f"HOI THOAI: {conversation['external_thread_id']}", "=" * 50]
        for message in by_conversation[conversation["id"]]:
            speaker = "KHACH" if message["direction"] == "INBOUND" else "NHAN VIEN"
            lines.append(f"{speaker}: {message['content']}")
            if message["direction"] == "INBOUND":
                label = labels[message["id"]]
                lines.append(f"  => {label['intent']} / {label['sentiment']}")
        blocks.append("\n".join(lines))
    return "\n\n".join(blocks) + "\n"


def generate(output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    account_rows = [{
        "id": CHANNEL_ID,
        "organization_id": ORG,
        "channel_type": "ZALO_SIM",
        "display_name": "Zalo EDU AI-05 demo",
        "external_account_id": "zalo-sim-edu-ai05",
        "team_id": TEAM,
        "default_assignee_user_id": ASSIGNEE,
        "status": "ACTIVE",
        "secret_ref": "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET",
        "created_at": iso(START),
        "updated_at": iso(START),
    }]
    conversation_rows: list[dict[str, str]] = []
    message_rows: list[dict[str, str]] = []
    annotation_rows: list[dict[str, str]] = []
    seen_contents: set[str] = set()
    for index, (profile_type, profile) in enumerate(profiles()):
        ordinal = index + 1
        conversation_id = uid("conversation", ordinal)
        split = "dev" if ordinal <= 40 else "test"
        offering = offering_for(index, profile_type, profile)
        workflow, turns = scenario(index, profile_type, offering)
        turns = rewrite_repeated_turns(turns, index, offering, workflow, seen_contents)
        started_at = START + timedelta(hours=index * 3, minutes=index % 4 * 7)
        occurred: list[datetime] = []
        for sequence, turn in enumerate(turns, start=1):
            timestamp = started_at + timedelta(minutes=(sequence - 1) * (7 + index % 4))
            occurred.append(timestamp)
            message_id = uid("message", ordinal, sequence)
            inbound = turn.direction == "INBOUND"
            message_rows.append({
                "id": message_id,
                "organization_id": ORG,
                "conversation_id": conversation_id,
                "direction": turn.direction,
                "external_message_id": f"zalo-ai05-edu-{ordinal:03d}-{sequence:02d}",
                "content_type": "TEXT",
                "content": turn.content,
                "sent_by_user_id": "" if inbound else ASSIGNEE,
                "delivery_status": "RECEIVED" if inbound else "SENT",
                "occurred_at": iso(timestamp),
                "interaction_id": "",
                "created_at": iso(timestamp),
            })
            if inbound:
                annotation_rows.append({
                    "message_id": message_id,
                    "conversation_id": conversation_id,
                    "organization_id": ORG,
                    "split": split,
                    "intent": turn.intent or "",
                    "sentiment": turn.sentiment or "",
                    "annotator": ANNOTATOR,
                    "annotation_version": VERSION,
                })
        last_inbound = max(timestamp for timestamp, turn in zip(occurred, turns) if turn.direction == "INBOUND")
        conversation_rows.append({
            "id": conversation_id,
            "organization_id": ORG,
            "channel_account_id": CHANNEL_ID,
            "external_thread_id": f"zalo-ai05-edu-{ordinal:03d}",
            "lead_id": profile["id"] if profile_type == "LEAD" else "",
            "customer_id": profile["id"] if profile_type == "CUSTOMER" else "",
            "assigned_user_id": ASSIGNEE,
            "status": "CLOSED" if workflow in {"complaint", "admissions_abandoned"} and ordinal % 2 == 0 else "PENDING" if workflow in {"academic_support", "schedule_change"} else "OPEN",
            "consent_status": "UNKNOWN",
            "last_inbound_at": iso(last_inbound),
            "last_message_at": iso(occurred[-1]),
            "created_at": iso(started_at),
            "updated_at": iso(occurred[-1]),
        })
    write_csv(output / "channel_accounts.csv", list(account_rows[0]), account_rows)
    write_csv(output / "conversations.csv", list(conversation_rows[0]), conversation_rows)
    write_csv(output / "conversation_messages.csv", list(message_rows[0]), message_rows)
    write_csv(output / "round_1_annotations.csv", list(annotation_rows[0]), annotation_rows)
    with (output / "edu_human_review.txt").open("w", encoding="utf-8", newline="\n") as file:
        file.write(render_human_review(conversation_rows, message_rows, annotation_rows))
    write_checksums(output, ["channel_accounts.csv", "conversations.csv", "conversation_messages.csv", "round_1_annotations.csv"])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    generate(args.output)


if __name__ == "__main__":
    main()
