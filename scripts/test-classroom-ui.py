"""课堂双端浏览器回归。需启动 localhost:8080/5173、MySQL，并安装 Python playwright。

仅创建随机测试账号和课程，finally 清理；真实媒体授权以浏览器人工操作为准。
运行：python scripts/test-classroom-ui.py
"""
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import random
import shutil
import subprocess
import time
import uuid
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
FRONT = os.environ.get('CLASSROOM_FRONT_URL', 'http://localhost:5173')
settings = {}
for path in [ROOT / '.env', ROOT / 'bishe' / '.env']:
    if path.exists():
        settings.update(dict(line.strip().split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line and not line.lstrip().startswith('#')))
settings.update(os.environ)
mysql = shutil.which('mysql')
if not mysql:
    raise RuntimeError('MySQL CLI is required')
db_env = {**os.environ, 'MYSQL_PWD': settings.get('DB_PASSWORD', '')}

def sql(query):
    result = subprocess.run([mysql, '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '-h', settings.get('DB_HOST', 'localhost'), '-P', settings.get('DB_PORT', '3306'), '-u', settings.get('DB_USERNAME', 'bishe_app'), settings.get('DB_NAME', 'bishe')], input=query, text=True, encoding='utf-8', capture_output=True, env=db_env, check=True)
    return result.stdout.strip()

teacher = 2_000_000_000 + random.randrange(1_000_000_000)
students = [1_000_000_000 + random.randrange(1_000_000_000) for _ in range(3)]
marker = '课堂UI验证-' + uuid.uuid4().hex[:8]
out = ROOT / 'test-results' / 'classroom'
out.mkdir(parents=True, exist_ok=True)

def token(user, role):
    encode = lambda data: base64.urlsafe_b64encode(data).decode().rstrip('=')
    payload = encode(f'{user}|{role}|{user}|{int(time.time()*1000)+7200000}|{uuid.uuid4()}'.encode())
    return payload + '.' + encode(hmac.new(settings['TOKEN_SECRET'].strip().encode(), payload.encode(), hashlib.sha256).digest())

def login(browser, user, role, name):
    context = browser.new_context(viewport={'width': 1440, 'height': 1000}, permissions=['camera', 'microphone'])
    auth = {'token': token(user, role), 'role': role, 'profile': {'teacherId' if role == 'TEACHER' else 'studentId': user, 'name' if role == 'TEACHER' else 'nickname': name}}
    context.add_init_script('const a = ' + json.dumps(auth) + "; localStorage.setItem('educhat_token',a.token);localStorage.setItem('educhat_role',a.role);localStorage.setItem('educhat_profile',JSON.stringify(a.profile));")
    page = context.new_page()
    page.on('pageerror', lambda error: errors.append(str(error)))
    return context, page

def api(page, method, path, body=None):
    response = page.evaluate("""async ({method,path,body}) => {
      const r=await fetch(path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+localStorage.getItem('educhat_token')},body:body===null?undefined:JSON.stringify(body)});return await r.json()
    }""", {'method': method, 'path': path, 'body': body})
    assert response.get('code') == 200, f'{method} {path}: {response.get("message")}'
    return response.get('data')

errors = []
try:
    sql(f"INSERT INTO teacher(teacher_id,name,password) VALUES({teacher},'{marker}','disabled-ui-test-account');" + ''.join(f"INSERT INTO student(student_id,nickname,password) VALUES({student},'课堂学生{i+1}','disabled-ui-test-account');" for i, student in enumerate(students)))
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True, executable_path=os.environ.get('CLASSROOM_CHROMIUM'), args=['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', '--autoplay-policy=no-user-gesture-required'])
        tc, tp = login(browser, teacher, 'TEACHER', marker)
        sc, sp = login(browser, students[0], 'STUDENT', '课堂学生1')
        tp.goto(FRONT + '/teacher/courses', wait_until='domcontentloaded')
        tp.wait_for_timeout(1500)
        print('teacher page loaded', flush=True)
        tp.screenshot(path=str(out / '00-teacher-page.png'))
        tp.get_by_role('button', name='创建新课程', exact=True).click()
        dialog = tp.get_by_role('dialog', name='创建新课程')
        dialog.get_by_placeholder('例如：高等数学 A').fill(marker)
        dialog.get_by_text('固定授课时间', exact=True).click()
        dialog.get_by_placeholder('选择上课时间').fill('09:30')
        dialog.get_by_placeholder('选择上课时间').press('Enter')
        dialog.get_by_text('学生加入课程需要申请', exact=True).click()
        dialog.get_by_text('限制课堂人数', exact=True).click()
        dialog.get_by_role('spinbutton').fill('2')
        tp.screenshot(path=str(out / '01-create-course.png'))
        dialog.get_by_role('button', name='创建', exact=True).click()
        expect(dialog).not_to_be_visible()
        course = next(c for c in api(tp, 'GET', '/api/course/teacher/mine') if c['courseName'] == marker)
        cid = course['courseId']
        assert course['classTime'] == '09:30' and course['maxStudents'] == 2 and course['approvalRequired']
        sp.goto(FRONT + '/student/courses', wait_until='domcontentloaded')
        sp.get_by_placeholder('输入课程号搜索').fill(str(cid))
        sp.get_by_role('button', name='搜索', exact=True).click()
        sp.get_by_role('button', name='申请加入', exact=True).click()
        expect(sp.get_by_role('button', name='等待教师审核')).to_be_disabled()
        assert api(sp, 'GET', '/api/course/student/mine') == []
        tp.locator('.application-panel').get_by_role('button', name='刷新', exact=True).click()
        tp.get_by_role('button', name='通过', exact=True).click()
        expect(tp.get_by_text('暂无待处理的入课申请')).to_be_visible()
        # 额外学生用于同轮多人抽取验证。
        sql(f"INSERT INTO course_personnel(course_id,student_id,student_score) VALUES({cid},{students[1]},0)")
        tp.get_by_role('button', name='上课', exact=True).click()
        tp.get_by_role('button', name='本地授课 线下课堂，发布签到与随机点名').click()
        expect(tp.get_by_role('button', name='开始上课', exact=True)).to_be_visible()
        tp.get_by_role('button', name='开始上课', exact=True).click()
        expect(tp.get_by_text('本地授课 · 正在上课')).to_be_visible()
        expect(sp.get_by_text(f'你的{marker}课程开始上课！本次授课为本地授课')).to_be_visible(timeout=20000)
        assert '/student/courses' in sp.url
        tp.locator('.draw-controls').get_by_role('spinbutton').fill('2')
        tp.get_by_role('button', name='开始点名', exact=True).click()
        expect(tp.locator('.student-spot')).to_have_count(2)
        expect(tp.get_by_role('button', name='再抽一次', exact=True)).to_be_enabled()
        tp.screenshot(path=str(out / '02-local-class.png'))
        tp.get_by_role('button', name='发布签到', exact=True).first.click()
        tp.locator('.checkin-panel').get_by_role('button', name='发布签到', exact=True).click()
        expect(sp.locator('.checkin-dot')).to_be_visible(timeout=20000)
        sp.locator('a[href="/student/check-in"]').click()
        sp.get_by_role('button', name='立即签到', exact=True).click()
        expect(sp.locator('.checkin-dot')).to_have_count(0, timeout=20000)
        tp.get_by_role('button', name='结束本次上课', exact=True).click()
        tp.locator('.el-message-box').get_by_role('button', name='确定', exact=True).click()
        expect(tp.get_by_role('heading', name='我的授课')).to_be_visible()
        tp.get_by_role('button', name='上课', exact=True).click()
        tp.get_by_role('button', name='网络授课 摄像头、屏幕共享与麦克风直播').click()
        tp.get_by_role('button', name='开启摄像头', exact=True).click()
        expect(tp.get_by_role('button', name='关闭摄像头', exact=True)).to_be_visible()
        tp.get_by_role('button', name='开启麦克风', exact=True).click()
        expect(tp.get_by_role('button', name='关闭麦克风', exact=True)).to_be_visible()
        tp.get_by_role('button', name='开始上课', exact=True).click()
        expect(sp.get_by_role('button', name='确认并进入直播间')).to_be_visible(timeout=20000)
        sp.get_by_role('button', name='确认并进入直播间').click()
        expect(sp.get_by_text('网络授课 · 已连接')).to_be_visible(timeout=30000)
        sp.wait_for_function("document.querySelector('video')?.videoWidth > 0")
        assert sp.locator('video').evaluate('(v)=>v.srcObject.getAudioTracks().length') == 1
        # 用画布媒体流模拟屏幕采集，检查双视频轨传输和教师预览。
        tp.evaluate("""() => { navigator.mediaDevices.getDisplayMedia=async()=>{const c=document.createElement('canvas');c.width=960;c.height=540;const x=c.getContext('2d');let n=0;setInterval(()=>{x.fillStyle='#153858';x.fillRect(0,0,960,540);x.fillStyle='white';x.font='40px sans-serif';x.fillText('Classroom screen '+n++,80,160)},100);return c.captureStream(10)}}""")
        tp.get_by_role('button', name='共享屏幕', exact=True).click()
        expect(sp.locator('video')).to_have_count(2, timeout=30000)
        sp.wait_for_function("Array.from(document.querySelectorAll('video')).every(v=>v.videoWidth>0)")
        tp.screenshot(path=str(out / '03-online-teacher.png'))
        sp.screenshot(path=str(out / '04-online-student.png'))
        tp.get_by_role('button', name='停止共享', exact=True).click()
        expect(sp.locator('video')).to_have_count(1, timeout=30000)
        # 第二个学生同时观看，以及手工重连。
        sc2, sp2 = login(browser, students[1], 'STUDENT', '课堂学生2')
        sp2.goto(sp.url, wait_until='domcontentloaded')
        expect(sp2.get_by_text('网络授课 · 已连接')).to_be_visible(timeout=30000)
        sp2.wait_for_function("document.querySelector('video')?.videoWidth > 0")
        sp.get_by_role('button', name='重新连接', exact=True).click()
        expect(sp.get_by_text('网络授课 · 已连接')).to_be_visible(timeout=30000)
        tp.get_by_role('button', name='结束本次上课', exact=True).click()
        tp.locator('.el-message-box').get_by_role('button', name='确定', exact=True).click()
        expect(sp.get_by_role('heading', name='本次上课已结束')).to_be_visible(timeout=20000)
        expect(sp2.get_by_role('heading', name='本次上课已结束')).to_be_visible(timeout=20000)
        # 临近上课时间的教师弹窗。
        reminder = api(tp, 'POST', '/api/course/create', {'courseName': marker+'提醒', 'fixedSchedule': True, 'classWeekday': 1, 'classTime': '09:30'})
        tp.clock.install(time='2026-11-02T09:26:00+08:00')
        tp.reload()
        expect(tp.get_by_text('上课提醒 · 前5分钟', exact=True)).to_be_visible(timeout=20000)
        tp.locator('.el-message-box').get_by_role('button', name='稍后上课').click()
        assert not errors, errors
        print('PASS: creation, application review, local notification, multi-draw, check-in dot, camera+microphone, dual video, multiple viewers, reconnect, end and reminder')
        browser.close()
finally:
    # 仅清理本次随机教师创建的数据；不操作其他账号。
    ids = sql(f'SELECT course_id FROM course WHERE teacher_id={teacher}')
    if ids:
        course_ids = ','.join(ids.splitlines())
        sql(f'DELETE u FROM check_in_user u JOIN check_in_record r ON r.check_in_id=u.check_in_id WHERE r.course_id IN ({course_ids}); DELETE FROM check_in_record WHERE course_id IN ({course_ids});' + ''.join(f'DELETE FROM {table} WHERE course_id IN ({course_ids});' for table in ['course_session','course_application','course_personnel','course']))
    sql(f'DELETE FROM student WHERE student_id IN ({",".join(map(str,students))}); DELETE FROM teacher WHERE teacher_id={teacher};')
