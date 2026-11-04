"""课程页面、资料权限、独立作答与错题选择回归；只创建和清理随机测试账号。
运行前启动前后端：python scripts/test-course-workspace-ui.py
可用 CLASSROOM_CHROMIUM 指定已安装 Chromium。
"""
import base64
import hashlib
import hmac
import io
import json
import os
from pathlib import Path
import random
import shutil
import subprocess
import time
import uuid
import zipfile
from datetime import datetime, timedelta
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
FRONT = os.environ.get('CLASSROOM_FRONT_URL', 'http://localhost:5173')
settings = {}
for path in [ROOT / '.env', ROOT / 'bishe' / '.env']:
    if path.exists():
        settings.update(dict(line.strip().split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line and not line.lstrip().startswith('#')))
settings.update(os.environ)
mysql = shutil.which('mysql')
db_env = {**os.environ, 'MYSQL_PWD': settings.get('DB_PASSWORD', '')}

def sql(query):
    r = subprocess.run([mysql, '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '-h', settings.get('DB_HOST', 'localhost'), '-P', settings.get('DB_PORT', '3306'), '-u', settings.get('DB_USERNAME', 'bishe_app'), settings.get('DB_NAME', 'bishe')], input=query, text=True, encoding='utf-8', capture_output=True, env=db_env, check=True)
    return r.stdout.strip()

teacher = 5_000_000_000_000 + random.randrange(1_000_000_000)
students = [teacher + i for i in range(1, 4)]
marker = '课程页面验证-' + uuid.uuid4().hex[:6]
out = ROOT / 'test-results' / 'course-workspace'
out.mkdir(parents=True, exist_ok=True)
errors = []
questions = []

def token(user, role):
    encode = lambda data: base64.urlsafe_b64encode(data).decode().rstrip('=')
    payload = encode(f'{user}|{role}|{user}|{int(time.time()*1000)+7200000}|{uuid.uuid4()}'.encode())
    return payload + '.' + encode(hmac.new(settings['TOKEN_SECRET'].strip().encode(), payload.encode(), hashlib.sha256).digest())

def login(browser, user, role, name):
    context = browser.new_context(viewport={'width': 1440, 'height': 1100}, accept_downloads=True)
    auth = {'token': token(user, role), 'role': role, 'profile': {'teacherId' if role == 'TEACHER' else 'studentId': user, 'name' if role == 'TEACHER' else 'nickname': name}}
    context.add_init_script('const a = ' + json.dumps(auth) + ";localStorage.setItem('educhat_token',a.token);localStorage.setItem('educhat_role',a.role);localStorage.setItem('educhat_profile',JSON.stringify(a.profile));")
    page = context.new_page()
    page.on('pageerror', lambda error: errors.append(str(error)))
    return context, page

def raw(page, method, path, body=None):
    return page.evaluate("""async ({method,path,body}) => {
      const r=await fetch(path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+localStorage.getItem('educhat_token')},body:body===null?undefined:JSON.stringify(body)});return await r.json()
    }""", {'method': method, 'path': path, 'body': body})

def api(page, method, path, body=None):
    result = raw(page, method, path, body)
    assert result.get('code') == 200, f'{method} {path}: {result.get("message")}'
    return result.get('data')

def collect_all(page):
    expect(page.locator('.question-picker')).to_have_count(0)
    page.get_by_role('button', name='加入错题本', exact=True).click()
    expect(page.locator('.question-picker')).to_have_count(2)
    page.get_by_role('checkbox', name='选择第1题', exact=True).click()
    expect(page.get_by_role('checkbox', name='选择第1题', exact=True)).to_have_attribute('aria-checked', 'true')
    page.get_by_text('全选', exact=True).click()
    expect(page.locator('.question-picker.checked')).to_have_count(2)
    page.screenshot(path=str(out/f'selection-{page.url.split("/")[-2]}.png'))
    with page.expect_response(lambda r: '/api/error-book/add-batch' in r.url) as response:
        page.get_by_role('button', name='确认加入', exact=True).click()
    assert response.value.json()['data']['selectedCount'] == 2
    page.get_by_role('button', name='取消加入', exact=True).click()
    expect(page.locator('.question-picker')).to_have_count(0)
    expect(page.get_by_role('checkbox', name='全选', exact=True)).to_have_count(0)
    expect(page.get_by_role('button', name='加入错题本', exact=True)).to_be_visible()

try:
    sql(f"INSERT INTO teacher(teacher_id,name,password) VALUES({teacher},'{marker}','disabled-ui-test');" + ''.join(f"INSERT INTO student(student_id,nickname,password) VALUES({s},'课程学生{i+1}','disabled-ui-test');" for i,s in enumerate(students)))
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True, executable_path=os.environ.get('CLASSROOM_CHROMIUM'))
        tc,tp = login(browser,teacher,'TEACHER',marker)
        sc,sp = login(browser,students[0],'STUDENT','课程学生1')
        oc,op = login(browser,students[2],'STUDENT','课程学生3')
        tp.goto(FRONT+'/teacher/courses')
        tp.get_by_role('button',name='创建新课程',exact=True).click()
        dialog=tp.get_by_role('dialog',name='创建新课程')
        dialog.get_by_placeholder('例如：高等数学 A').fill(marker)
        dialog.get_by_text('学生加入课程需要申请',exact=True).click()
        dialog.get_by_role('button',name='创建课程',exact=True).click()
        expect(dialog).not_to_be_visible()
        cid=api(tp,'GET','/api/course/teacher/mine')[0]['courseId']
        sp.goto(FRONT+'/student/courses')
        api(sp,'POST','/api/course/join',{'courseId':cid})
        tp.reload()
        expect(tp.locator('.application-badge')).to_have_text('1')
        expect(tp.locator('a[href="/teacher/courses"] .count-badge')).to_have_text('1')
        expect(tp.locator('.course-card button')).to_have_count(1)
        tp.locator('.course-card').get_by_role('button',name='编辑').click()
        expect(tp.locator('.course-hero h1')).to_have_text(marker)
        expect(tp.locator('.el-drawer')).to_have_count(0)
        tp.get_by_role('link',name='入课申请').click()
        tp.get_by_role('button',name='通过',exact=True).click()
        expect(tp.get_by_text('暂无待处理的入课申请')).to_be_visible()
        api(tp,'PUT',f'/api/course/{cid}/settings',{'courseName':marker,'approvalRequired':False,'maxStudents':3,'showStudentRanking':True})
        sql(f"INSERT INTO course_personnel(course_id,student_id,student_score,joined_at) VALUES({cid},{students[1]},90,'2026-01-02');UPDATE course_personnel SET student_score=20,joined_at='2026-01-01' WHERE course_id={cid} AND student_id={students[0]};")
        sp.reload()
        expect(sp.locator('.application-status')).to_be_visible()
        sp.get_by_role('button',name='关闭入课申请',exact=True).click()
        expect(sp.locator('.application-status')).to_have_count(0)
        sp.reload()
        expect(sp.locator('.course-card')).to_have_count(1)
        expect(sp.locator('.application-status')).to_have_count(0)
        sp.get_by_role('button',name='课程详情').click()
        expect(sp.locator('.course-hero h1')).to_have_text(marker)
        expect(sp.locator('.el-drawer')).to_have_count(0)
        expect(sp.locator('.el-slider')).to_have_count(0)
        sp.get_by_role('link',name='班级成员',exact=True).click()
        expect(sp.locator('.el-table__body tr')).to_have_count(2)
        expect(sp.locator('.el-table__body tr').first).to_contain_text('课程学生2')
        tp.get_by_role('link',name='班级成员',exact=True).click()
        tp.locator('.el-switch').click()
        expect(sp.get_by_text('教师未开放课程成绩排名，仅展示你的分数，其他同学分数不可见')).to_be_visible(timeout=15000)
        expect(sp.locator('.el-table__body tr').first).to_contain_text('课程学生1')
        expect(sp.locator('.el-table__body tr').nth(1)).to_contain_text('未开放')
        expect(tp.locator('.el-table__body tr').nth(1)).to_contain_text('90.0')
        sp.screenshot(path=str(out/'01-student-members-private.png'))
        tp.locator('.el-switch').click()
        tp.get_by_role('link',name='课程设置',exact=True).click()
        tp.reload()
        expect(tp.get_by_role('spinbutton')).to_have_attribute('min','2')
        tp.get_by_role('spinbutton').fill('4')
        # 生成本机测试封面，不依赖外部资源。
        png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=')
        tp.locator('input[type=file]').set_input_files({'name':'cover.png','mimeType':'image/png','buffer':png})
        expect(tp.locator('.cover-editor img')).to_be_visible()
        tp.get_by_role('button',name='保存课程设置',exact=True).click()
        expect(tp.locator('.course-hero .hero-image')).to_be_visible()
        assert api(tp,'GET',f'/api/course/{cid}/overview')['course']['maxStudents']==4
        tp.screenshot(path=str(out/'02-teacher-course.png'))
        tp.get_by_role('link',name='资料',exact=True).click()
        pdf=b'%PDF-1.4\ncourse material integration'
        tp.locator('input[type=file]').set_input_files({'name':'测试讲义.pdf','mimeType':'application/pdf','buffer':pdf})
        expect(tp.locator('.material-row')).to_have_count(1)
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w') as z: z.writestr('word/document.xml','<document>test</document>')
        tp.locator('input[type=file]').set_input_files({'name':'测试说明.docx','mimeType':'application/vnd.openxmlformats-officedocument.wordprocessingml.document','buffer':stream.getvalue()})
        expect(tp.locator('.material-row')).to_have_count(2)
        materials=api(tp,'GET',f'/api/course/{cid}/materials')
        pdf_id=next(m['id'] for m in materials if m['originalName'].endswith('.pdf'))
        stored=sql(f'SELECT storage_name FROM course_material WHERE id={pdf_id}')
        upload_root=Path(settings.get('UPLOAD_DIR',str(ROOT/'bishe'/'uploads')))
        disk=upload_root/'private'/'course-materials'/stored
        assert disk.exists(), 'material must be stored on backend disk'
        for private in ['private','PRIVATE']:
            denied=tp.request.get(FRONT+f'/uploads/{private}/course-materials/{stored}')
            assert denied.status in [403,404], f'private static path exposed: {private}'
        op.goto(FRONT+'/student/courses')
        assert raw(op,'GET',f'/api/course/{cid}/materials')['code']==403
        assert raw(op,'GET',f'/api/course/{cid}/materials/{pdf_id}/download')['code']==403
        sp.get_by_role('link',name='资料',exact=True).click()
        expect(sp.locator('.material-row')).to_have_count(2)
        with sp.expect_download() as download:
            sp.locator('.material-row').filter(has_text='测试讲义.pdf').get_by_role('button',name='下载',exact=True).click()
        assert download.value.suggested_filename=='测试讲义.pdf'
        assert Path(download.value.path()).read_bytes()==pdf
        sp.screenshot(path=str(out/'03-student-materials.png'))
        print('PASS: course pages, request badges/dismiss, ranking privacy, cover/settings, private PDF/Word storage and downloads',flush=True)
        # 同时有多门课程时，全部课程应包含每门课程的任务。
        cid2=api(tp,'POST','/api/course/create',{'courseName':marker+'第二门'})['courseId']
        api(sp,'POST','/api/course/join',{'courseId':cid2})
        for typ in [1,2]:
            questions.append(api(tp,'POST','/api/question/create',{'content':marker+('选择题 1+1=?' if typ==1 else '判断题 2+2=4'),'questionType':typ,'answer':'A' if typ==1 else '正确','options':['2','3','4','5']})['questionId'])
        refs=[{'questionId':questions[i],'questionType':i+1,'score':5} for i in range(2)]
        exam=api(tp,'POST','/api/exam/publish',{'courseId':cid,'timeLimit':30,'questions':refs})['examId']
        hw=api(tp,'POST','/api/homework/publish',{'courseId':cid2,'deadline':(datetime.now()+timedelta(hours=1)).isoformat(timespec='seconds'),'questions':refs})['homeworkId']
        for kind,ident,answer_label in [('exam',exam,'开始考试'),('homework',hw,'开始作答')]:
            sp.goto(FRONT+'/student/tasks?tab='+kind)
            expect(sp.locator('.el-select__placeholder')).to_contain_text('全部课程')
            sp.get_by_role('button',name=answer_label,exact=True).click()
            expect(sp).to_have_url(FRONT+f'/student/assessment/{kind}/{ident}')
            expect(sp.locator('.q-block')).to_have_count(2)
            expect(sp.locator('.el-dialog:visible')).to_have_count(0)
            sp.get_by_text('A. 2',exact=True).click()
            sp.reload()
            expect(sp.get_by_role('radio',name='A. 2',exact=True)).to_be_checked()
            sp.get_by_text('正确',exact=True).click()
            sp.get_by_role('button',name='提交'+('考试' if kind=='exam' else '作业'),exact=True).click()
            sp.locator('.el-message-box').get_by_role('button',name='确定',exact=True).click()
            expect(sp.get_by_role('button',name='加入错题本',exact=True)).to_be_visible()
            collect_all(sp)
            sp.screenshot(path=str(out/f'04-{kind}-review.png'))
        # 隐藏成绩期间仍可查看本人作答，但不能通过错题本提前取得参考答案。
        hidden=api(tp,'POST','/api/homework/publish',{'courseId':cid2,'deadline':(datetime.now()+timedelta(hours=1)).isoformat(timespec='seconds'),'hideResultUntilPublish':1,'questions':refs})['homeworkId']
        api(sp,'POST','/api/homework/submit',{'homeworkId':hidden,'answers':[{'questionId':questions[0],'questionType':1,'studentAnswer':'A'},{'questionId':questions[1],'questionType':2,'studentAnswer':'错误'}]})
        sp.goto(FRONT+'/student/tasks?tab=homework')
        sp.locator('.task-card').filter(has_text=f'作业 #{hidden}').get_by_role('button',name='查看作答',exact=True).click()
        expect(sp.get_by_role('button',name='加入错题本',exact=True)).to_be_disabled()
        expect(sp.get_by_text('参考答案：',exact=False)).to_have_count(0)
        api(tp,'POST',f'/api/homework/{hidden}/publish-result')
        expect(sp.get_by_role('button',name='加入错题本',exact=True)).to_be_enabled(timeout=15000)
        # 三类作答共享圆形勾选、全选与取消交互。
        session=api(tp,'POST','/api/classroom/start',{'courseId':cid,'mode':'LOCAL'})['sessionId']
        template=api(tp,'POST','/api/quiz/templates',{'name':marker+'小测','hideAnswers':False,'countScore':False,'showRanking':True,'questions':refs})['id']
        quiz=api(tp,'POST','/api/quiz/publish',{'templateId':template,'sessionId':session})['id']
        api(sp,'POST',f'/api/quiz/{quiz}/submit',{'answers':[{'questionId':questions[i],'questionType':i+1,'studentAnswer':'A' if i==0 else '正确'} for i in range(2)]})
        api(tp,'POST',f'/api/quiz/{quiz}/end')
        api(tp,'POST',f'/api/classroom/{session}/end')
        sp.goto(FRONT+f'/student/quiz/{quiz}')
        collect_all(sp)
        summary=api(sp,'GET',f'/api/course/{cid}/summary')
        assert summary['examCount']==1 and summary['quizCount']==1
        sp.goto(FRONT+f'/student/courses/{cid}/summary')
        expect(sp.get_by_role('heading',name='个人学习总结')).to_be_visible()
        expect(sp.locator('.course-workspace .el-loading-mask')).to_have_count(0)
        sp.screenshot(path=str(out/'05-personal-summary.png'))
        # 结课前没有评分条，结课后可以评分，提交后只显示数字。
        api(tp,'POST','/api/course/finish',{'courseId':cid})
        sp.goto(FRONT+f'/student/courses/{cid}')
        expect(sp.locator('.el-slider')).to_be_visible()
        sp.get_by_role('button',name='提交评分',exact=True).click()
        expect(sp.locator('.rating-result')).to_contain_text('8.0')
        expect(sp.locator('.el-slider')).to_have_count(0)
        sp.reload()
        expect(sp.locator('.rating-result')).to_contain_text('8.0')
        sp.goto(FRONT+'/student/chat')
        expect(sp.get_by_text('支持文本、图片、引用回复与消息撤回；实时消息经 WebSocket 推送')).to_have_count(0)
        sp.locator('.system-item').click()
        expect(sp.get_by_text('暂无消息，打个招呼吧')).to_have_count(0)
        # 恢复课程占用进行中名额，5门上限由前后端共同限制。
        api(tp,'POST','/api/course/unfinish',{'courseId':cid})
        for i in range(3): api(tp,'POST','/api/course/create',{'courseName':marker+f'容量{i}'})
        assert raw(tp,'POST','/api/course/create',{'courseName':marker+'第六门'})['code']!=200
        tp.goto(FRONT+'/teacher/courses')
        expect(tp.get_by_role('button',name='创建新课程',exact=True)).to_be_disabled()
        expect(tp.locator('.course-card')).to_have_count(5)
        tp.screenshot(path=str(out/'06-five-course-cards.png'))
        # 删除资料同时清理磁盘文件。
        api(tp,'DELETE',f'/api/course/{cid}/materials/{pdf_id}')
        assert not disk.exists()
        assert not errors, errors
        print('PASS: independent exam/homework + draft restore, three-type batch selection/cancel, all courses, summary, finished-only rating, chat cleanup, five-course cap and material cleanup',flush=True)
        browser.close()
finally:
    # 测试专用账号不可登录；只删除本次教师和学生的数据。
    ids=sql(f'SELECT course_id FROM course WHERE teacher_id={teacher}')
    if ids:
        cids=','.join(ids.splitlines())
        # 用后端级联保证关联题目、答卷和文件清理逻辑也得到覆盖。
        import urllib.request
        covers=sql(f"SELECT cover_url FROM course WHERE teacher_id={teacher} AND cover_url IS NOT NULL AND cover_url<>''").splitlines()
        for cid in ids.splitlines():
            request=urllib.request.Request('http://localhost:8080/api/course/delete',data=json.dumps({'courseId':int(cid)}).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+token(teacher,'TEACHER')},method='POST')
            with urllib.request.urlopen(request) as response:
                assert json.load(response)['code']==200
        for cover in covers:
            if cover.startswith('/uploads/image/'):
                name=cover.removeprefix('/uploads/image/')
                if '/' not in name and '\\' not in name:
                    (Path(settings.get('UPLOAD_DIR',str(ROOT/'bishe'/'uploads')))/'image'/name).unlink(missing_ok=True)
    student_ids=','.join(map(str,students))
    sql(f'DELETE FROM teacher_evaluation WHERE teacher_id={teacher};DELETE FROM error_book WHERE student_id IN ({student_ids});DELETE FROM quiz_template WHERE teacher_id={teacher};')
    # 题库表彼此分开存储，且只清理这名测试教师创建的题。
    sql(f'DELETE FROM question_choice_option WHERE question_id IN (SELECT question_id FROM question_choice WHERE publisher_id={teacher});DELETE FROM question_choice WHERE publisher_id={teacher};DELETE FROM question_judge WHERE publisher_id={teacher};DELETE FROM question_answer WHERE publisher_id={teacher};DELETE FROM student WHERE student_id IN ({student_ids});DELETE FROM teacher WHERE teacher_id={teacher};')
