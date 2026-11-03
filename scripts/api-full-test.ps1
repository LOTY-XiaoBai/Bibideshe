#Requires -Version 5.1
<#
.SYNOPSIS
  Full-stack API smoke test for Docker deployment.

.DESCRIPTION
  Covers admin / teacher / student flows.
  Requires CAPTCHA_EXPOSE_CODE=true on backend.

.EXAMPLE
  cd E:\bibideshe
  powershell -ExecutionPolicy Bypass -File .\scripts\api-full-test.ps1
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost",
    [string]$FrontUrl = "http://localhost",
    [string]$Password = "Test123456!",
    [string]$AdminPassword = "Admin123456!",
    # On 2nd+ runs pass the admin id printed by first bootstrap, e.g. -BootstrapAdminId admin88959
    [string]$BootstrapAdminId = ""
)

$ErrorActionPreference = "Continue"
$script:PassCount = 0
$script:FailCount = 0
$script:Results = New-Object System.Collections.Generic.List[object]

function Write-Step([string]$msg) {
    Write-Host ""
    Write-Host "==== $msg ====" -ForegroundColor Cyan
}

function Add-Result([string]$name, [bool]$ok, [string]$detail = "") {
    if ($ok) {
        $script:PassCount++
        Write-Host "[PASS] $name" -ForegroundColor Green
    } else {
        $script:FailCount++
        Write-Host "[FAIL] $name  $detail" -ForegroundColor Red
    }
    $script:Results.Add([pscustomobject]@{ Name = $name; Ok = $ok; Detail = $detail }) | Out-Null
}

function Convert-Body($body) {
    if ($null -eq $body) { return $null }
    if ($body -is [string]) { return $body }
    return ($body | ConvertTo-Json -Depth 12 -Compress)
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        $Body = $null,
        [string]$Token = "",
        [hashtable]$Query = $null
    )

    $uri = "${BaseUrl}${Path}"
    if ($Query -and $Query.Count -gt 0) {
        $parts = @()
        foreach ($k in $Query.Keys) {
            $parts += ("{0}={1}" -f [uri]::EscapeDataString([string]$k), [uri]::EscapeDataString([string]$Query[$k]))
        }
        $uri = "${uri}?" + ($parts -join "&")
    }

    $headers = @{ "Accept" = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    $params = @{
        Method     = $Method
        Uri        = $uri
        Headers    = $headers
        TimeoutSec = 60
    }

    $json = Convert-Body $Body
    if ($null -ne $json) {
        $params["ContentType"] = "application/json; charset=utf-8"
        $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }

    try {
        $resp = Invoke-RestMethod @params
        if ($null -eq $resp.code) { return $resp }
        if ([int]$resp.code -ne 200) {
            throw "biz code=$($resp.code) message=$($resp.message)"
        }
        return $resp
    } catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $detail = $_.ErrorDetails.Message
        }
        throw "$Method $Path failed: $detail"
    }
}

function Assert-Api {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    try {
        $data = & $Action
        Add-Result $Name $true
        return $data
    } catch {
        Add-Result $Name $false $_.Exception.Message
        return $null
    }
}

function Get-CaptchaPair {
    $cap = Invoke-Api -Method GET -Path "/api/captcha"
    $key = $cap.data.captchaKey
    $code = $cap.data.captchaCode
    if (-not $code) {
        throw "captchaCode missing. Set CAPTCHA_EXPOSE_CODE=true and rebuild backend."
    }
    return @{ Key = $key; Code = $code }
}

function New-Login([string]$RolePath, [string]$Id, [string]$Pwd) {
    $c = Get-CaptchaPair
    $resp = Invoke-Api -Method POST -Path $RolePath -Body @{
        id          = "$Id"
        password    = $Pwd
        captchaKey  = $c.Key
        captchaCode = $c.Code
    }
    return $resp.data
}

$adminToken = $null
$adminId = $null
$teacherToken = $null
$teacherId = $null
$studentToken = $null
$studentId = $null
$student2Token = $null
$student2Id = $null
$courseId = $null
$qChoiceId = $null
$qJudgeId = $null
$qAnswerId = $null
$examId = $null
$homeworkId = $null
$checkInId = $null
$adId = $null
$orderId = $null
$groupId = $null
$messageId = $null
$ts = Get-Date -Format "yyyyMMddHHmmss"

Write-Host "BaseUrl = $BaseUrl"
Write-Host "FrontUrl= $FrontUrl"
Write-Host "Time    = $ts"

Write-Step "0. Health"
Assert-Api "frontend home" {
    $null = Invoke-WebRequest -Uri $FrontUrl -UseBasicParsing -TimeoutSec 15
    $true
} | Out-Null

$capOk = Assert-Api "GET /api/captcha" {
    $c = Invoke-Api -Method GET -Path "/api/captcha"
    if (-not $c.data.captchaKey) { throw "missing captchaKey" }
    if (-not $c.data.captchaCode) { throw "missing captchaCode (need CAPTCHA_EXPOSE_CODE=true)" }
    $c
}

if (-not $capOk) {
    Write-Host "Captcha probe failed. See C:\Users\30881\Desktop\毕设文档\DOCKER_DEPLOY.md" -ForegroundColor Yellow
    exit 1
}

Write-Step "1. Admin bootstrap/login"
$avail = Assert-Api "GET /api/admin/register/available" {
    Invoke-Api -Method GET -Path "/api/admin/register/available"
}

if ($avail -and $avail.data.available -eq $true) {
    $regAdmin = Assert-Api "POST /api/admin/register" {
        Invoke-Api -Method POST -Path "/api/admin/register" -Body @{ password = $AdminPassword }
    }
    if ($regAdmin) {
        $adminId = [string]$regAdmin.data.adminId
        Write-Host "  Admin ID = $adminId" -ForegroundColor Yellow
    }
} else {
    Write-Host "  Admin already exists; use -BootstrapAdminId <id> (from first bootstrap printout)" -ForegroundColor Yellow
    if ($BootstrapAdminId) { $adminId = $BootstrapAdminId }
    elseif ($env:ADMIN_ID) { $adminId = $env:ADMIN_ID }
}

if ($adminId) {
    $loginAdmin = Assert-Api "POST /api/admin/login" {
        New-Login "/api/admin/login" $adminId $AdminPassword
    }
    if ($loginAdmin) {
        $adminToken = $loginAdmin.token
        if (-not $adminId) { $adminId = [string]$loginAdmin.profile.adminId }
    }
} else {
    Add-Result "admin login" $false "unknown adminId"
}

Write-Step "2. Teacher/Student register+login"
$regTeacher = Assert-Api "POST /api/teacher/register" {
    $c = Get-CaptchaPair
    Invoke-Api -Method POST -Path "/api/teacher/register" -Body @{
        name        = "Teacher$ts"
        password    = $Password
        signature   = "auto-test"
        captchaKey  = $c.Key
        captchaCode = $c.Code
    }
}
if ($regTeacher) {
    $teacherId = [int64]$regTeacher.data.teacherId
    Write-Host "  Teacher ID = $teacherId"
}

$loginTeacher = Assert-Api "POST /api/teacher/login" {
    if (-not $teacherId) { throw "no teacherId" }
    New-Login "/api/teacher/login" $teacherId $Password
}
if ($loginTeacher) { $teacherToken = $loginTeacher.token }

$regStudent = Assert-Api "POST /api/student/register (A)" {
    $c = Get-CaptchaPair
    Invoke-Api -Method POST -Path "/api/student/register" -Body @{
        nickname    = "StudentA$ts"
        password    = $Password
        signature   = "student-a"
        captchaKey  = $c.Key
        captchaCode = $c.Code
    }
}
if ($regStudent) {
    $studentId = [int64]$regStudent.data.studentId
    Write-Host "  Student1 ID = $studentId"
}

$loginStudent = Assert-Api "POST /api/student/login (A)" {
    if (-not $studentId) { throw "no studentId" }
    New-Login "/api/student/login" $studentId $Password
}
if ($loginStudent) { $studentToken = $loginStudent.token }

$regStudent2 = Assert-Api "POST /api/student/register (B)" {
    $c = Get-CaptchaPair
    Invoke-Api -Method POST -Path "/api/student/register" -Body @{
        nickname    = "StudentB$ts"
        password    = $Password
        signature   = "student-b"
        captchaKey  = $c.Key
        captchaCode = $c.Code
    }
}
if ($regStudent2) {
    $student2Id = [int64]$regStudent2.data.studentId
    Write-Host "  Student2 ID = $student2Id"
}

$loginStudent2 = Assert-Api "POST /api/student/login (B)" {
    if (-not $student2Id) { throw "no student2Id" }
    New-Login "/api/student/login" $student2Id $Password
}
if ($loginStudent2) { $student2Token = $loginStudent2.token }

Assert-Api "GET /api/teacher/profile" {
    Invoke-Api -Method GET -Path "/api/teacher/profile" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/student/profile" {
    Invoke-Api -Method GET -Path "/api/student/profile" -Token $studentToken
} | Out-Null

Assert-Api "PUT /api/student/profile" {
    Invoke-Api -Method PUT -Path "/api/student/profile" -Token $studentToken -Body @{
        displayName = "StudentA-renamed-$ts"
        signature   = "updated-by-script"
    }
} | Out-Null

Write-Step "3. Course"
$course = Assert-Api "POST /api/course/create" {
    Invoke-Api -Method POST -Path "/api/course/create" -Token $teacherToken -Body @{
        teacherId  = $teacherId
        courseName = "AutoCourse$ts"
    }
}
if ($course) {
    $courseId = [int64]$course.data.courseId
    Write-Host "  Course ID = $courseId"
}

Assert-Api "GET /api/course/teacher/mine" {
    Invoke-Api -Method GET -Path "/api/course/teacher/mine" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/course/search" {
    Invoke-Api -Method GET -Path "/api/course/search" -Token $studentToken -Query @{ courseId = $courseId }
} | Out-Null

Assert-Api "POST /api/course/join (A)" {
    Invoke-Api -Method POST -Path "/api/course/join" -Token $studentToken -Body @{
        studentId = $studentId
        courseId  = $courseId
    }
} | Out-Null

Assert-Api "POST /api/course/join (B)" {
    Invoke-Api -Method POST -Path "/api/course/join" -Token $student2Token -Body @{
        studentId = $student2Id
        courseId  = $courseId
    }
} | Out-Null

Assert-Api "GET /api/course/{id}/members" {
    Invoke-Api -Method GET -Path "/api/course/$courseId/members" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/course/student/mine" {
    Invoke-Api -Method GET -Path "/api/course/student/mine" -Token $studentToken
} | Out-Null

Write-Step "4. Question bank"
$q1 = Assert-Api "POST /api/question/create choice" {
    Invoke-Api -Method POST -Path "/api/question/create" -Token $teacherToken -Body @{
        publisherId  = $teacherId
        questionType = 1
        content      = "1+1=?"
        answer       = "A"
        isPublic     = 1
        options      = @("2", "3", "4", "5")
    }
}
if ($q1) { $qChoiceId = [int64]$q1.data.questionId }

$q2 = Assert-Api "POST /api/question/create judge" {
    Invoke-Api -Method POST -Path "/api/question/create" -Token $teacherToken -Body @{
        publisherId  = $teacherId
        questionType = 2
        content      = "Earth is round"
        answer       = "true"
        isPublic     = 0
    }
}
if ($q2) { $qJudgeId = [int64]$q2.data.questionId }

$q3 = Assert-Api "POST /api/question/create essay" {
    Invoke-Api -Method POST -Path "/api/question/create" -Token $teacherToken -Body @{
        publisherId  = $teacherId
        questionType = 3
        content      = "What is an OS?"
        answer       = "system software managing hardware and software"
        isPublic     = 0
    }
}
if ($q3) { $qAnswerId = [int64]$q3.data.questionId }

Assert-Api "GET /api/question/mine" {
    Invoke-Api -Method GET -Path "/api/question/mine" -Token $teacherToken
} | Out-Null

if ($adminToken -and $qChoiceId) {
    Assert-Api "GET /api/admin/question/pending" {
        Invoke-Api -Method GET -Path "/api/admin/question/pending" -Token $adminToken
    } | Out-Null

    Assert-Api "POST /api/admin/question/audit approve" {
        Invoke-Api -Method POST -Path "/api/admin/question/audit" -Token $adminToken -Body @{
            questionId   = $qChoiceId
            questionType = 1
            approve      = $true
        }
    } | Out-Null

    Assert-Api "GET /api/question/public" {
        Invoke-Api -Method GET -Path "/api/question/public" -Token $teacherToken
    } | Out-Null
}

Write-Step "5. Exam"
$exam = Assert-Api "POST /api/exam/publish" {
    Invoke-Api -Method POST -Path "/api/exam/publish" -Token $teacherToken -Body @{
        courseId               = $courseId
        teacherId              = $teacherId
        timeLimit              = 30
        hideResultUntilPublish = 1
        questions              = @(
            @{ questionId = $qChoiceId; questionType = 1; score = 40 },
            @{ questionId = $qJudgeId;  questionType = 2; score = 30 },
            @{ questionId = $qAnswerId; questionType = 3; score = 30 }
        )
    }
}
if ($exam) {
    $examId = [int64]$exam.data.examId
    Write-Host "  Exam ID = $examId"
}

Assert-Api "GET /api/exam/course/{courseId}" {
    Invoke-Api -Method GET -Path "/api/exam/course/$courseId" -Token $studentToken
} | Out-Null

Assert-Api "POST /api/exam/{id}/start" {
    Invoke-Api -Method POST -Path "/api/exam/$examId/start" -Token $studentToken
} | Out-Null

Assert-Api "POST /api/exam/submit" {
    Invoke-Api -Method POST -Path "/api/exam/submit" -Token $studentToken -Body @{
        examId    = $examId
        studentId = $studentId
        answers   = @(
            @{ questionId = $qChoiceId; questionType = 1; studentAnswer = "A" },
            @{ questionId = $qJudgeId;  questionType = 2; studentAnswer = "true" },
            @{ questionId = $qAnswerId; questionType = 3; studentAnswer = "OS manages computer resources" }
        )
    }
} | Out-Null

Assert-Api "GET /api/exam/{id}/pending-grade" {
    Invoke-Api -Method GET -Path "/api/exam/$examId/pending-grade" -Token $teacherToken
} | Out-Null

Assert-Api "POST /api/exam/grade essay" {
    Invoke-Api -Method POST -Path "/api/exam/grade" -Token $teacherToken -Body @{
        examId       = $examId
        questionId   = $qAnswerId
        questionType = 3
        studentId    = $studentId
        score        = 25
        annotation   = "ok"
    }
} | Out-Null

Assert-Api "POST /api/exam/{id}/publish-result" {
    Invoke-Api -Method POST -Path "/api/exam/$examId/publish-result" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/exam/{id}/scores" {
    Invoke-Api -Method GET -Path "/api/exam/$examId/scores" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/exam/{id}/review" {
    Invoke-Api -Method GET -Path "/api/exam/$examId/review" -Token $studentToken
} | Out-Null

Assert-Api "GET /api/exam/student/{id}/records" {
    Invoke-Api -Method GET -Path "/api/exam/student/$studentId/records" -Token $studentToken
} | Out-Null

Write-Step "6. Homework"
$deadline = (Get-Date).AddDays(3).ToString("yyyy-MM-ddTHH:mm:ss")
$hw = Assert-Api "POST /api/homework/publish" {
    Invoke-Api -Method POST -Path "/api/homework/publish" -Token $teacherToken -Body @{
        courseId               = $courseId
        teacherId              = $teacherId
        deadline               = $deadline
        hideResultUntilPublish = 1
        questions              = @(
            @{ questionId = $qChoiceId; questionType = 1; score = 50 },
            @{ questionId = $qJudgeId;  questionType = 2; score = 50 }
        )
    }
}
if ($hw) {
    $homeworkId = [int64]$hw.data.homeworkId
    Write-Host "  Homework ID = $homeworkId"
}

Assert-Api "GET /api/homework/course/{courseId}" {
    Invoke-Api -Method GET -Path "/api/homework/course/$courseId" -Token $studentToken
} | Out-Null

Assert-Api "POST /api/homework/submit" {
    Invoke-Api -Method POST -Path "/api/homework/submit" -Token $studentToken -Body @{
        homeworkId = $homeworkId
        studentId  = $studentId
        answers    = @(
            @{ questionId = $qChoiceId; questionType = 1; studentAnswer = "B" },
            @{ questionId = $qJudgeId;  questionType = 2; studentAnswer = "false" }
        )
    }
} | Out-Null

Assert-Api "POST /api/homework/{id}/publish-result" {
    Invoke-Api -Method POST -Path "/api/homework/$homeworkId/publish-result" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/homework/{id}/scores" {
    Invoke-Api -Method GET -Path "/api/homework/$homeworkId/scores" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/homework/{id}/review" {
    Invoke-Api -Method GET -Path "/api/homework/$homeworkId/review" -Token $studentToken
} | Out-Null

Write-Step "7. Check-in"
$classroom = Assert-Api "POST /api/classroom/start" {
    Invoke-Api -Method POST -Path "/api/classroom/start" -Token $teacherToken -Body @{ courseId = $courseId; mode = 'LOCAL' }
}
$ciDeadline = (Get-Date).AddHours(2).ToString("yyyy-MM-ddTHH:mm:ss")
$ci = Assert-Api "POST /api/check-in/publish" {
    Invoke-Api -Method POST -Path "/api/check-in/publish" -Token $teacherToken -Body @{
        teacherId  = $teacherId
        courseId   = $courseId
        deadline   = $ciDeadline
        scoreValue = 5
    }
}
if ($ci) {
    $checkInId = [int64]$ci.data.checkInId
    Write-Host "  CheckIn ID = $checkInId"
}

Assert-Api "POST /api/check-in/sign" {
    Invoke-Api -Method POST -Path "/api/check-in/sign" -Token $studentToken -Body @{
        checkInId = $checkInId
        studentId = $studentId
    }
} | Out-Null

Assert-Api "GET /api/check-in/status/{id}" {
    Invoke-Api -Method GET -Path "/api/check-in/status/$checkInId" -Token $studentToken
} | Out-Null

Assert-Api "GET /api/check-in/detail/{id}" {
    Invoke-Api -Method GET -Path "/api/check-in/detail/$checkInId" -Token $teacherToken
} | Out-Null

Assert-Api "POST /api/check-in/end" {
    Invoke-Api -Method POST -Path "/api/check-in/end" -Token $teacherToken -Query @{ checkInId = $checkInId }
} | Out-Null

Assert-Api "POST /api/check-in/update-status" {
    Invoke-Api -Method POST -Path "/api/check-in/update-status" -Token $teacherToken -Body @{
        checkInId = $checkInId
        teacherId = $teacherId
        studentId = $student2Id
        status    = 1
    }
} | Out-Null

Assert-Api "GET /api/check-in/course/{courseId}" {
    Invoke-Api -Method GET -Path "/api/check-in/course/$courseId" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/check-in/student/{id}/records" {
    Invoke-Api -Method GET -Path "/api/check-in/student/$studentId/records" -Token $studentToken
} | Out-Null

Write-Step "8. Scores / rate / error-book"
Assert-Api "GET /api/course/student/mine/scores" {
    Invoke-Api -Method GET -Path "/api/course/student/mine/scores" -Token $studentToken
} | Out-Null

Assert-Api "GET /api/course/student/mine/rank" {
    Invoke-Api -Method GET -Path "/api/course/student/mine/rank" -Token $studentToken -Query @{ courseId = $courseId }
} | Out-Null

Assert-Api "GET /api/teacher-evaluation/rateable" {
    Invoke-Api -Method GET -Path "/api/teacher-evaluation/rateable" -Token $studentToken
} | Out-Null

Assert-Api "POST /api/teacher-evaluation/rate" {
    Invoke-Api -Method POST -Path "/api/teacher-evaluation/rate" -Token $studentToken -Body @{
        courseId  = $courseId
        teacherId = $teacherId
        score     = 9
    }
} | Out-Null

Assert-Api "GET /api/teacher-evaluation/score/{teacherId}" {
    Invoke-Api -Method GET -Path "/api/teacher-evaluation/score/$teacherId" -Token $teacherToken
} | Out-Null

Assert-Api "GET /api/error-book/{studentId}" {
    Invoke-Api -Method GET -Path "/api/error-book/$studentId" -Token $studentToken
} | Out-Null

Write-Step "9. Friend / chat / group"
Assert-Api "GET /api/friend/search" {
    Invoke-Api -Method GET -Path "/api/friend/search" -Token $studentToken -Query @{ userId = $student2Id }
} | Out-Null

Assert-Api "POST /api/friend/request" {
    Invoke-Api -Method POST -Path "/api/friend/request" -Token $studentToken -Body @{
        receiverId = $student2Id
    }
} | Out-Null

$recv = Assert-Api "GET /api/friend/requests/received" {
    Invoke-Api -Method GET -Path "/api/friend/requests/received" -Token $student2Token
}
$requestId = $null
if ($recv -and $recv.data -and $recv.data.Count -gt 0) {
    $requestId = [int64]$recv.data[0].requestId
}

Assert-Api "POST /api/friend/request/accept" {
    if (-not $requestId) { throw "missing requestId" }
    Invoke-Api -Method POST -Path "/api/friend/request/accept" -Token $student2Token -Body @{
        requestId = $requestId
    }
} | Out-Null

Assert-Api "GET /api/friend/list" {
    Invoke-Api -Method GET -Path "/api/friend/list" -Token $studentToken
} | Out-Null

Assert-Api "GET /api/friend/pk" {
    Invoke-Api -Method GET -Path "/api/friend/pk" -Token $studentToken -Query @{
        friendId = $student2Id
        courseId = $courseId
    }
} | Out-Null

$priv = Assert-Api "POST /api/chat/private/send" {
    Invoke-Api -Method POST -Path "/api/chat/private/send" -Token $studentToken -Body @{
        receiverId  = $student2Id
        content     = "hello private $ts"
        messageType = 0
        referenceId = 0
    }
}
if ($priv -and $priv.data -and $priv.data.messageId) {
    $messageId = [int64]$priv.data.messageId
}

Assert-Api "GET /api/chat/private/{friendId}" {
    Invoke-Api -Method GET -Path "/api/chat/private/$student2Id" -Token $studentToken
} | Out-Null

$grp = Assert-Api "POST /api/group/create" {
    Invoke-Api -Method POST -Path "/api/group/create" -Token $studentToken -Body @{
        groupName        = "Group$ts"
        groupDescription = "api smoke"
    }
}
if ($grp) {
    $groupId = [int64]$grp.data.groupId
    Write-Host "  Group ID = $groupId"
}

Assert-Api "POST /api/group/join" {
    Invoke-Api -Method POST -Path "/api/group/join" -Token $student2Token -Body @{
        groupId = $groupId
    }
} | Out-Null

Assert-Api "GET /api/group/mine" {
    Invoke-Api -Method GET -Path "/api/group/mine" -Token $studentToken
} | Out-Null

Assert-Api "GET /api/group/{id}/members" {
    Invoke-Api -Method GET -Path "/api/group/$groupId/members" -Token $studentToken
} | Out-Null

Assert-Api "POST /api/chat/group/send" {
    Invoke-Api -Method POST -Path "/api/chat/group/send" -Token $studentToken -Body @{
        groupId     = $groupId
        content     = "group msg $ts"
        messageType = 0
        referenceId = 0
    }
} | Out-Null

Assert-Api "GET /api/chat/group/{groupId}" {
    Invoke-Api -Method GET -Path "/api/chat/group/$groupId" -Token $studentToken
} | Out-Null

Assert-Api "POST /api/chat/private/send sensitive" {
    Invoke-Api -Method POST -Path "/api/chat/private/send" -Token $studentToken -Body @{
        receiverId  = $student2Id
        content     = "test badword trigger"
        messageType = 0
        referenceId = 0
    }
} | Out-Null

if ($messageId) {
    Assert-Api "POST /api/chat/recall" {
        Invoke-Api -Method POST -Path "/api/chat/recall" -Token $studentToken -Body @{
            messageId = $messageId
        }
    } | Out-Null
}

Assert-Api "GET /api/warning/list" {
    Invoke-Api -Method GET -Path "/api/warning/list" -Token $studentToken
} | Out-Null

Assert-Api "GET /api/warning/unread" {
    Invoke-Api -Method GET -Path "/api/warning/unread" -Token $studentToken
} | Out-Null

Write-Step "10. Shop"
if ($adminToken) {
    $ad = Assert-Api "POST /api/shop/admin/ad/publish" {
        Invoke-Api -Method POST -Path "/api/shop/admin/ad/publish" -Token $adminToken -Body @{
            title    = "Item$ts"
            content  = "auto test item"
            imageUrl = "/uploads/demo.png"
            price    = 19.9
        }
    }
    if ($ad) {
        $adId = [int64]$ad.data.adId
        Write-Host "  Ad ID = $adId"
    }

    Assert-Api "POST /api/shop/admin/ad/update" {
        Invoke-Api -Method POST -Path "/api/shop/admin/ad/update" -Token $adminToken -Body @{
            adId     = $adId
            title    = "ItemUpdated$ts"
            content  = "updated"
            imageUrl = "/uploads/demo.png"
            price    = 29.9
        }
    } | Out-Null

    Assert-Api "GET /api/shop/admin/ad/list" {
        Invoke-Api -Method GET -Path "/api/shop/admin/ad/list" -Token $adminToken -Query @{ page = 1; size = 10 }
    } | Out-Null
}

Assert-Api "GET /api/shop/ad/list" {
    Invoke-Api -Method GET -Path "/api/shop/ad/list" -Token $studentToken
} | Out-Null

Assert-Api "GET /api/shop/ad/{adId}" {
    if (-not $adId) { throw "no adId" }
    Invoke-Api -Method GET -Path "/api/shop/ad/$adId" -Token $studentToken
} | Out-Null

$order = Assert-Api "POST /api/shop/order/create" {
    Invoke-Api -Method POST -Path "/api/shop/order/create" -Token $studentToken -Body @{
        adId = $adId
    }
}
if ($order) {
    $orderId = $null
    if ($order.data.order -and $order.data.order.orderId) { $orderId = [int64]$order.data.order.orderId }
    elseif ($order.data.orderId) { $orderId = [int64]$order.data.orderId }
    Write-Host "  Order ID = $orderId"
}

if ($adminToken -and $orderId) {
    Assert-Api "GET /api/shop/admin/order/list" {
        Invoke-Api -Method GET -Path "/api/shop/admin/order/list" -Token $adminToken -Query @{ page = 1; size = 10 }
    } | Out-Null

    Assert-Api "GET /api/shop/admin/order/pending-count" {
        Invoke-Api -Method GET -Path "/api/shop/admin/order/pending-count" -Token $adminToken
    } | Out-Null

    Assert-Api "POST /api/shop/admin/order/logistics" {
        Invoke-Api -Method POST -Path "/api/shop/admin/order/logistics" -Token $adminToken -Body @{
            orderId       = $orderId
            logisticsInfo = "SF123456789CN"
        }
    } | Out-Null
}

Assert-Api "POST /api/shop/order/confirm" {
    Invoke-Api -Method POST -Path "/api/shop/order/confirm" -Token $studentToken -Query @{ orderId = $orderId }
} | Out-Null

Assert-Api "POST /api/shop/order/feedback" {
    Invoke-Api -Method POST -Path "/api/shop/order/feedback" -Token $studentToken -Body @{
        orderId         = $orderId
        rating          = 5
        feedbackContent = "good"
    }
} | Out-Null

Assert-Api "POST /api/shop/order/after-sale" {
    Invoke-Api -Method POST -Path "/api/shop/order/after-sale" -Token $studentToken -Body @{
        orderId = $orderId
        content = "want refund test"
    }
} | Out-Null

if ($adminToken) {
    Assert-Api "POST /api/shop/admin/order/after-sale/reply" {
        Invoke-Api -Method POST -Path "/api/shop/admin/order/after-sale/reply" -Token $adminToken -Body @{
            orderId = $orderId
            reply   = "approved"
        }
    } | Out-Null

    Assert-Api "GET /api/shop/admin/evaluation/list" {
        Invoke-Api -Method GET -Path "/api/shop/admin/evaluation/list" -Token $adminToken
    } | Out-Null
}

Assert-Api "GET /api/shop/order/mine" {
    Invoke-Api -Method GET -Path "/api/shop/order/mine" -Token $studentToken
} | Out-Null

Write-Step "11. Admin moderation"
if ($adminToken) {
    Assert-Api "GET /api/admin/account/students" {
        Invoke-Api -Method GET -Path "/api/admin/account/students" -Token $adminToken -Query @{ page = 1; size = 10 }
    } | Out-Null

    Assert-Api "GET /api/admin/account/teachers" {
        Invoke-Api -Method GET -Path "/api/admin/account/teachers" -Token $adminToken -Query @{ page = 1; size = 10 }
    } | Out-Null

    Assert-Api "GET /api/admin/course/list" {
        Invoke-Api -Method GET -Path "/api/admin/course/list" -Token $adminToken -Query @{ page = 1; size = 10 }
    } | Out-Null

    Assert-Api "GET /api/admin/chat/records" {
        Invoke-Api -Method GET -Path "/api/admin/chat/records" -Token $adminToken -Query @{ page = 1; size = 10 }
    } | Out-Null

    Assert-Api "GET /api/admin/group/list" {
        Invoke-Api -Method GET -Path "/api/admin/group/list" -Token $adminToken -Query @{ page = 1; size = 10 }
    } | Out-Null

    Assert-Api "GET /api/admin/special-word/list" {
        Invoke-Api -Method GET -Path "/api/admin/special-word/list" -Token $adminToken
    } | Out-Null

    Assert-Api "POST /api/admin/special-word/add" {
        Invoke-Api -Method POST -Path "/api/admin/special-word/add" -Token $adminToken -Body @{
            word = "badword$ts"
        }
    } | Out-Null

    Assert-Api "GET /api/admin/special-word/violations" {
        Invoke-Api -Method GET -Path "/api/admin/special-word/violations" -Token $adminToken -Query @{ page = 1; size = 10 }
    } | Out-Null

    try {
        $viol = Invoke-Api -Method GET -Path "/api/admin/special-word/violations" -Token $adminToken -Query @{ page = 1; size = 5 }
        $list = $null
        if ($viol.data.list) { $list = $viol.data.list }
        elseif ($viol.data -is [System.Array]) { $list = $viol.data }
        if ($list -and $list.Count -gt 0) {
            $vid = $list[0].violationId
            Assert-Api "POST /api/admin/special-word/handle" {
                Invoke-Api -Method POST -Path "/api/admin/special-word/handle" -Token $adminToken -Body @{
                    violationId = $vid
                    action      = "IGNORE"
                    note        = "auto-test"
                }
            } | Out-Null
        } else {
            Add-Result "POST /api/admin/special-word/handle (skip)" $true "no violations"
        }
    } catch {
        Add-Result "violations handle" $false $_.Exception.Message
    }
} else {
    Add-Result "admin moderation block" $false "no adminToken"
}

Write-Step "12. Course finish/unfinish"
Assert-Api "POST /api/course/finish" {
    Invoke-Api -Method POST -Path "/api/course/finish" -Token $teacherToken -Body @{
        teacherId = $teacherId
        courseId  = $courseId
    }
} | Out-Null

Assert-Api "POST /api/course/unfinish" {
    Invoke-Api -Method POST -Path "/api/course/unfinish" -Token $teacherToken -Body @{
        teacherId = $teacherId
        courseId  = $courseId
    }
} | Out-Null

Write-Host ""
Write-Host "========================================"
Write-Host ("DONE: PASS={0}  FAIL={1}  TOTAL={2}" -f $script:PassCount, $script:FailCount, ($script:PassCount + $script:FailCount))
Write-Host "========================================"
Write-Host ""
Write-Host "Accounts (use in browser):" -ForegroundColor Yellow
Write-Host "  Admin   : $adminId / $AdminPassword"
Write-Host "  Teacher : $teacherId / $Password"
Write-Host "  Student1: $studentId / $Password"
Write-Host "  Student2: $student2Id / $Password"
Write-Host "  Course  : $courseId"
Write-Host "  Exam/HW/CI: $examId / $homeworkId / $checkInId"
Write-Host "  Ad/Order/Group: $adId / $orderId / $groupId"
Write-Host ""
Write-Host "Frontend/API entry: $FrontUrl   (Swagger is disabled by default)"
Write-Host "Features: C:\Users\30881\Desktop\毕设文档\FEATURES.md"

if ($script:FailCount -gt 0) {
    Write-Host ""
    Write-Host "Failed cases:" -ForegroundColor Red
    $script:Results | Where-Object { -not $_.Ok } | ForEach-Object {
        Write-Host (" - {0}: {1}" -f $_.Name, $_.Detail)
    }
    exit 1
}

exit 0
