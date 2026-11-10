"""错题本回归。仅在已核实的隔离本地后端运行，会创建测试账号和作答数据。"""

import json
import os
import secrets
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def local_url(variable):
    value = os.environ[variable].rstrip("/")
    url = urlsplit(value)
    if url.scheme != "http" or url.hostname != "127.0.0.1" or not url.port or url.path:
        raise SystemExit(f"{variable} 必须指定隔离环境的 127.0.0.1 地址和端口")
    return value


def api(method, path, body=None, user=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if user:
        headers["Authorization"] = "Bearer " + user["token"]
    request = Request(backend + path, None if body is None else json.dumps(body).encode(), headers, method=method)
    try:
        with urlopen(request, timeout=15) as response:
            result = json.load(response)
    except HTTPError as response:
        result = json.load(response)
    assert result["code"] == expected, f"{method} {path}: 预期 {expected}，实际 {result['code']}（{result['message']}）"
    return result.get("data")


def captcha():
    data = api("GET", "/api/captcha")
    return {key: data[key] for key in ("captchaKey", "captchaCode")}


def register(role, marker):
    name_key = "name" if role == "teacher" else "nickname"
    password = secrets.token_hex(12)
    profile = api("POST", f"/api/{role}/register", {name_key: marker, "password": password, **captcha()})
    return api("POST", f"/api/{role}/login", {"id": str(profile[role + "Id"]), "password": password, **captcha()})


def fixture():
    marker = "错题重做-" + secrets.token_hex(4)
    teacher = register("teacher", marker)
    student = register("student", marker)
    student_id = student["profile"]["studentId"]
    course = api("POST", "/api/course/create", {"courseName": marker}, teacher)["courseId"]
    api("POST", "/api/course/join", {"courseId": course}, student)
    questions = [
        api("POST", "/api/question/create", {
            "content": marker + " 判断题", "questionType": 2, "answer": "正确",
        }, teacher),
        api("POST", "/api/question/create", {
            "content": marker + " 选择题", "questionType": 1, "answer": "A", "options": ["甲", "乙"],
        }, teacher),
        api("POST", "/api/question/create", {
            "content": marker + " 多选练习", "questionType": 1, "answer": "A,C", "options": ["甲", "乙", "丙"],
        }, teacher),
        api("POST", "/api/question/create", {
            "content": marker + " 解答题", "questionType": 3, "answer": "用于对照的参考解答",
        }, teacher),
    ]
    refs = [{key: question[key] for key in ("questionId", "questionType")} for question in questions]
    deadline = datetime.now(timezone(timedelta(hours=8))) + timedelta(hours=1)
    homework = api("POST", "/api/homework/publish", {
        "courseId": course, "deadline": deadline.replace(tzinfo=None, microsecond=0).isoformat(),
        "hideResultUntilPublish": 1, "questions": [{**ref, "score": 5} for ref in refs],
    }, teacher)["homeworkId"]
    api("POST", "/api/homework/submit", {
        "homeworkId": homework, "answers": [
            {**ref, "studentAnswer": answer} for ref, answer in zip(refs, ("错误", "B", "B", "原作答"))
        ],
    }, student)
    collect = {"source": "HOMEWORK", "sourceId": homework, "questions": refs}
    assert not api("GET", f"/api/error-book/{student_id}", user=student)
    assert api("POST", "/api/error-book/add-batch", collect, student, expected=400) is None
    api("POST", f"/api/homework/{homework}/publish-result", user=teacher)
    api("POST", "/api/error-book/add-batch", collect, student)
    return student, student_id, refs, homework, course, teacher


def test_hidden_list(student, student_id, refs):
    rows = api("GET", f"/api/error-book/{student_id}", user=student)
    assert len(rows) == len(refs)
    assert all(row["question"].get("answer") is None for row in rows), "列表在展开之前返回了参考答案"


def test_explicit_reveal(student, student_id, ref):
    other = register("student", "错题权限-" + secrets.token_hex(4))
    api("GET", f"/api/error-book/{student_id}", user=other, expected=403)
    api("POST", "/api/error-book/answers", {"questions": [ref]}, other, expected=403)
    answers = api("POST", "/api/error-book/answers", {"questions": [ref]}, student)
    assert answers == [{**ref, "answer": "正确"}]


def test_redo_paper(student, ref):
    other = register("student", "错题重做权限-" + secrets.token_hex(4))
    api("POST", "/api/error-book/redo", {"questions": []}, student, expected=400)
    api("POST", "/api/error-book/redo", {"questions": [ref]}, other, expected=403)
    draft = api("POST", "/api/error-book/redo", {"questions": [ref]}, student)
    assert len(draft["questions"]) == 1
    question = draft["questions"][0]["question"]
    assert {key: question[key] for key in ref} == ref
    assert "answer" not in question and "studentAnswer" not in draft["questions"][0]
    assert api("GET", f"/api/error-book/redo/{draft['id']}", user=student) == draft
    api("GET", f"/api/error-book/redo/{draft['id']}", user=other, expected=404)


def test_redo_submission(student, student_id, refs, homework, course):
    original_routes = [
        f"/api/homework/{homework}/review",
        f"/api/homework/student/{student_id}/records",
        f"/api/course/{course}/summary",
    ]
    before = [api("GET", path, user=student) for path in original_routes]
    other = register("student", "重做历史权限-" + secrets.token_hex(4))
    history = "/api/error-book/redo/history"
    previous_history = api("GET", history, user=student)
    draft = api("POST", "/api/error-book/redo", {"questions": refs}, student)
    assert draft["questions"][2]["question"]["multiple"] is True
    redo_id = draft["id"]
    api("GET", f"{history}/{redo_id}", user=student, expected=404)
    assert api("GET", history, user=student) == previous_history
    submit = f"/api/error-book/redo/{redo_id}/submit"
    answers = [
        {**refs[0], "studentAnswer": "错误"},
        {**refs[2], "studentAnswer": "C,A"},
        {**refs[3], "studentAnswer": "重做解答"},
    ]
    api("POST", submit, {"answers": answers}, other, expected=404)
    api("POST", submit, {"answers": answers + [{**refs[0], "questionId": 99999999}]}, student, expected=400)
    result = api("POST", submit, {"answers": answers}, student)
    assert [result[key] for key in ("correct", "incorrect", "unanswered")] == [1, 1, 1]
    assert [row["status"] for row in result["answers"]] == ["INCORRECT", "UNANSWERED", "CORRECT", "SELF_REVIEW"]
    assert result["answers"][3]["studentAnswer"] == "重做解答"
    assert result["answers"][3]["question"]["answer"] == "用于对照的参考解答"
    assert api("POST", submit, {"answers": answers}, student) == result
    assert [row["id"] for row in api("GET", history, user=student)] == [redo_id] + [row["id"] for row in previous_history]
    assert api("GET", f"{history}/{redo_id}", user=student) == result
    assert api("GET", history, user=other) == []
    api("GET", f"{history}/{redo_id}", user=other, expected=404)
    assert [api("GET", path, user=student) for path in original_routes] == before


def test_deleted_question(student, student_id, ref):
    admin_id = os.environ.get("ERROR_BOOK_ADMIN_ID")
    password = os.environ.get("ERROR_BOOK_ADMIN_PASSWORD")
    if not admin_id:
        password = secrets.token_hex(12)
        admin_id = api("POST", "/api/admin/register", {
            "password": password, "bootstrapSecret": os.environ["ADMIN_BOOTSTRAP_SECRET"],
        })["adminId"]
    admin = api("POST", "/api/admin/login", {"id": admin_id, "password": password, **captcha()})
    redo = "/api/error-book/redo"
    history = f"{redo}/history"
    first_id = api("GET", history, user=student)[0]["id"]
    first_result = api("GET", f"{history}/{first_id}", user=student)
    selection = {"questions": [ref]}
    draft = api("POST", redo, selection, student)
    stem = draft["questions"][0]["question"]["content"]
    api("POST", "/api/admin/question/delete", ref, admin)
    rows = api("GET", f"/api/error-book/{student_id}", user=student)
    deleted = next(row for row in rows if all(row["question"][key] == value for key, value in ref.items()))
    assert deleted["archived"] and deleted["question"]["lost"] and deleted["question"]["content"] == stem
    assert deleted["question"].get("answer") is None
    assert api("POST", "/api/error-book/answers", selection, student) == [{**ref, "answer": "正确"}]
    api("POST", redo, selection, student, expected=400)
    result = api("POST", f"{redo}/{draft['id']}/submit", {
        "answers": [{**ref, "studentAnswer": "正确"}],
    }, student)
    assert result["correct"] == 1 and result["answers"][0]["question"]["content"] == stem
    assert api("GET", f"{history}/{first_id}", user=student) == first_result


def test_exam_and_quiz_publication(student, student_id, course, teacher):
    refs = []
    for source in ("考试", "小测"):
        question = api("POST", "/api/question/create", {
            "questionType": 2, "content": source + "公布权限", "answer": "正确",
        }, teacher)
        refs.append({key: question[key] for key in ("questionId", "questionType")})

    exam_ref, quiz_ref = refs
    exam = api("POST", "/api/exam/publish", {
        "courseId": course, "timeLimit": 30, "hideResultUntilPublish": 1,
        "questions": [{**exam_ref, "score": 5}],
    }, teacher)["examId"]
    api("POST", f"/api/exam/{exam}/start", user=student)
    exam_result = api("POST", "/api/exam/submit", {
        "examId": exam, "answers": [{**exam_ref, "studentAnswer": "错误"}],
    }, student)
    assert exam_result["answers"][0]["question"].get("answer") is None
    exam_collect = {"source": "EXAM", "sourceId": exam, "questions": [exam_ref]}
    api("POST", "/api/error-book/add-batch", exam_collect, student, expected=400)
    api("POST", "/api/error-book/answers", {"questions": [exam_ref]}, student, expected=403)
    api("POST", f"/api/exam/{exam}/publish-result", user=teacher)
    api("POST", "/api/error-book/add-batch", exam_collect, student)

    session = api("POST", "/api/classroom/start", {"courseId": course, "mode": "LOCAL"}, teacher)["sessionId"]
    template = api("POST", "/api/quiz/templates", {
        "name": "小测公布权限", "hideAnswers": True, "countScore": True, "showRanking": False,
        "questions": [{**quiz_ref, "score": 5}],
    }, teacher)["id"]
    quiz = api("POST", "/api/quiz/publish", {"templateId": template, "sessionId": session}, teacher)["id"]
    quiz_result = api("POST", f"/api/quiz/{quiz}/submit", {
        "answers": [{**quiz_ref, "studentAnswer": "错误"}],
    }, student)
    assert not quiz_result["answersVisible"] and quiz_result["questions"][0]["question"].get("answer") is None
    quiz_collect = {"source": "QUIZ", "sourceId": quiz, "questions": [quiz_ref]}
    api("POST", "/api/error-book/add-batch", quiz_collect, student, expected=403)
    api("POST", "/api/error-book/redo", {"questions": [quiz_ref]}, student, expected=403)
    api("POST", f"/api/quiz/{quiz}/end", user=teacher)
    api("POST", f"/api/classroom/{session}/end", user=teacher)
    api("POST", "/api/error-book/add-batch", quiz_collect, student)

    original_routes = [
        f"/api/exam/{exam}/review",
        f"/api/exam/student/{student_id}/records",
        f"/api/quiz/{quiz}",
        f"/api/course/{course}/summary",
    ]
    before = [api("GET", path, user=student) for path in original_routes]
    draft = api("POST", "/api/error-book/redo", {"questions": refs}, student)
    result = api("POST", f"/api/error-book/redo/{draft['id']}/submit", {
        "answers": [{**ref, "studentAnswer": "正确"} for ref in refs],
    }, student)
    assert result["correct"] == 2
    assert [api("GET", path, user=student) for path in original_routes] == before


def test_answer_visibility(page, front):
    from playwright.sync_api import expect

    page.goto(front + "/student/error-book", wait_until="domcontentloaded")
    cards = page.locator("article")
    judge = cards.filter(has_text="判断题")
    choice = cards.filter(has_text="选择题")
    expect(cards).to_have_count(4)
    expect(page.get_by_text("参考答案", exact=True)).to_have_count(0)
    expect(choice.get_by_text("（正确答案）")).to_have_count(0)
    judge.get_by_role("button", name="显示答案").click()
    expect(judge.get_by_text("参考答案", exact=True)).to_be_visible()
    expect(choice.get_by_text("（正确答案）")).to_have_count(0)
    page.reload(wait_until="domcontentloaded")
    expect(judge.get_by_text("参考答案", exact=True)).to_be_visible()
    expect(choice.get_by_text("（正确答案）")).to_have_count(0)
    judge.get_by_role("button", name="隐藏答案").click()
    expect(judge.get_by_text("参考答案", exact=True)).to_have_count(0)
    page.get_by_role("button", name="全部展开", exact=True).click()
    expect(choice.get_by_text("（正确答案）")).to_have_count(3)
    expect(judge.get_by_text("参考答案", exact=True)).to_be_visible()
    page.get_by_role("button", name="全部隐藏", exact=True).click()
    expect(page.get_by_text("参考答案", exact=True)).to_have_count(0)
    expect(choice.get_by_text("（正确答案）")).to_have_count(0)


def test_redo_navigation(page, front, student):
    from playwright.sync_api import expect

    judge = page.locator("article").filter(has_text="判断题")
    redo = page.get_by_role("button", name="重做错题", exact=True)
    confirm = page.get_by_role("button", name="确认重做", exact=True)
    judge.get_by_role("button", name="显示答案").click()
    expect(judge.get_by_text("参考答案", exact=True)).to_be_visible()
    redo.click()
    expect(judge.get_by_text("参考答案", exact=True)).to_have_count(0)
    expect(confirm).to_be_disabled()
    judge.get_by_role("checkbox").check()
    page.get_by_role("button", name="取消重做", exact=True).click()
    expect(page.get_by_role("checkbox")).to_have_count(0)
    expect(judge.get_by_text("参考答案", exact=True)).to_be_visible()

    redo.click()
    expect(confirm).to_be_disabled()
    judge.get_by_role("checkbox").check()
    confirm.click()
    expect(page.get_by_role("heading", name="重做错题", exact=True)).to_be_visible()
    expect(page.locator("article")).to_have_count(1)
    correct = page.get_by_role("radio", name="正确", exact=True)
    page.get_by_text("正确", exact=True).click()
    redo_id = page.url.rsplit("/", 1)[1]
    page.reload(wait_until="domcontentloaded")
    expect(correct).to_be_checked()
    page.get_by_role("button", name="返回错题本", exact=True).click()
    dialog = page.get_by_role("dialog")
    expect(dialog.get_by_text("返回将清除本次重做记录")).to_be_visible()
    dialog.get_by_role("button", name="继续作答", exact=True).click()
    expect(correct).to_be_checked()
    page.get_by_role("button", name="返回错题本", exact=True).click()
    dialog.get_by_role("button", name="确认返回", exact=True).click()
    expect(page).to_have_url(front + "/student/error-book")
    expect(page.get_by_role("checkbox")).to_have_count(0)
    api("GET", f"/api/error-book/redo/{redo_id}", user=student, expected=404)
    assert api("GET", "/api/error-book/redo/history", user=student) == []


def test_redo_results(page):
    from playwright.sync_api import expect

    page.get_by_role("button", name="重做错题", exact=True).click()
    picks = page.get_by_role("checkbox")
    expect(picks).to_have_count(4)
    for pick in picks.all():
        pick.check()
    page.get_by_role("button", name="确认重做", exact=True).click()
    expect(page.locator("article")).to_have_count(4)
    page.get_by_text("错误", exact=True).click()
    multiple = page.locator("article").filter(has_text="多选练习")
    multiple.get_by_text("A. 甲", exact=True).click()
    multiple.get_by_text("C. 丙", exact=True).click()
    page.get_by_placeholder("请输入解答").fill("自行作答示例")
    page.get_by_role("button", name="提交重做", exact=True).click()
    dialog = page.get_by_role("dialog")
    expect(dialog.get_by_text("还有 1 道题未作答", exact=False)).to_be_visible()
    dialog.get_by_role("button", name="确认提交", exact=True).click()
    expect(page.get_by_role("heading", name="重做结果", exact=True)).to_be_visible()
    chart = page.get_by_role("img", name="正确率 33.3%，错误 33.3%，未答 33.3%")
    expect(chart).to_be_visible()
    expect(page.get_by_text("客观题得分 1 / 3", exact=True)).to_be_visible()
    expect(page.get_by_text("自行对照", exact=True)).to_be_visible()
    expect(page.get_by_text("自行作答示例", exact=True)).to_be_visible()
    page.get_by_role("button", name="返回错题本", exact=True).click()
    expect(page.get_by_role("checkbox")).to_have_count(0)
    page.get_by_role("button", name="重做历史", exact=True).click()
    expect(page.get_by_role("heading", name="重做历史", exact=True)).to_be_visible()
    page.get_by_role("link", name="查看结果", exact=True).click()
    expect(chart).to_be_visible()


def run_browser(student):
    from playwright.sync_api import sync_playwright

    front = local_url("ERROR_BOOK_FRONT_URL")
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=os.environ.get("ERROR_BOOK_CHROMIUM"))
        context = browser.new_context()
        context.route(lambda url: urlsplit(url).netloc != urlsplit(front).netloc, lambda route: route.abort())
        profile = context.request.get(front + "/api/student/profile", headers={
            "Authorization": "Bearer " + student["token"],
        }).json()
        assert profile["code"] == 200 and profile["data"]["userId"] == student["profile"]["studentId"]
        context.add_init_script(f"""const auth = {json.dumps(student)};
            localStorage.setItem('educhat_token', auth.token);
            localStorage.setItem('educhat_role', auth.role);
            localStorage.setItem('educhat_profile', JSON.stringify(auth.profile));
        """)
        page = context.new_page()
        page.set_default_timeout(8000)
        test_answer_visibility(page, front)
        print("PASS: 浏览器单题、批量显隐及刷新后恢复")
        test_redo_navigation(page, front, student)
        print("PASS: 浏览器勾选、取消、独立作答、刷新草稿及放弃返回")
        test_redo_results(page)
        print("PASS: 浏览器交卷、得分、正确/错误/未答百分比与历史回看")


if __name__ == "__main__":
    if os.environ.get("ERROR_BOOK_ISOLATED_TEST") != "true":
        raise SystemExit("核实独立本地数据库和后端后，才可设置 ERROR_BOOK_ISOLATED_TEST=true")
    backend = local_url("ERROR_BOOK_BACKEND_URL")
    data = fixture()
    test_hidden_list(*data[:3])
    print("PASS: 未公布题目无法收录，默认列表不返回参考答案")
    test_explicit_reveal(data[0], data[1], data[2][0])
    print("PASS: 只可显式展开本人收录的题目")
    if os.environ.get("ERROR_BOOK_FRONT_URL"):
        run_browser(data[0])
    test_redo_paper(data[0], data[2][0])
    print("PASS: 重做取题仅返回本人所选题目，交卷前不下发答案")
    test_redo_submission(*data[:5])
    print("PASS: 判分、历史与权限，重做和原始作答及成绩隔离")
    test_deleted_question(data[0], data[1], data[2][0])
    print("PASS: 删除后可复习、不能开启新重做，已开始的作答和历史可查看")
    test_exam_and_quiz_publication(data[0], data[1], data[4], data[5])
    print("PASS: 考试和小测公布前不可收录或取答案，重做后原作答和成绩一致")
