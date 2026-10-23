<?php
declare(strict_types=1);

// Staff-only server-rendered catalog manager for the Django REST API.
// Override GEA_API_BASE_URL in the PHP host if deploying against another Django API.

$apiBase = rtrim(getenv('GEA_API_BASE_URL') ?: 'https://graphics-enhancement-advisor-1.onrender.com/api/v1', '/');
$secureCookie = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off');
session_set_cookie_params(['httponly' => true, 'secure' => $secureCookie, 'samesite' => 'Lax']);
session_start();

function h(mixed $value): string {
    return htmlspecialchars((string)$value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function csrfToken(): string {
    if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(32));
    return $_SESSION['csrf'];
}

function verifyCsrf(): void {
    $sent = (string)($_POST['csrf'] ?? '');
    if ($sent === '' || !hash_equals((string)($_SESSION['csrf'] ?? ''), $sent)) {
        http_response_code(400);
        exit('Invalid form token. Reload the page and try again.');
    }
}

function api(string $method, string $path, ?array $body = null, ?string $token = null): array {
    global $apiBase;
    $url = $apiBase . '/' . ltrim($path, '/');
    $curl = curl_init($url);
    $headers = ['Accept: application/json'];
    if ($body !== null) $headers[] = 'Content-Type: application/json';
    if ($token !== null) $headers[] = 'Authorization: Bearer ' . $token;
    curl_setopt_array($curl, [
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 5,
        CURLOPT_TIMEOUT => 15,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
    ]);
    if ($body !== null) curl_setopt($curl, CURLOPT_POSTFIELDS, json_encode($body, JSON_THROW_ON_ERROR));
    $raw = curl_exec($curl);
    $error = curl_error($curl);
    $status = (int)curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    curl_close($curl);
    if ($raw === false) return ['status' => 0, 'data' => ['detail' => $error ?: 'Could not reach the API.']];
    $data = json_decode($raw, true);
    return ['status' => $status, 'data' => is_array($data) ? $data : []];
}

function flash(string $message, string $type = 'success'): void {
    $_SESSION['flash'] = ['message' => $message, 'type' => $type];
}

function redirectHome(): never {
    header('Location: ' . strtok($_SERVER['REQUEST_URI'], '?'));
    exit;
}

function apiError(array $data): string {
    if (isset($data['detail']) && is_string($data['detail'])) return $data['detail'];
    foreach ($data as $field => $messages) {
        $first = is_array($messages) ? ($messages[0] ?? '') : $messages;
        if (is_string($first) && $first !== '') return ucfirst((string)$field) . ': ' . $first;
    }
    return 'The API rejected the request.';
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verifyCsrf();
    $action = (string)($_POST['action'] ?? '');

    if ($action === 'login') {
        $email = trim((string)($_POST['email'] ?? ''));
        $password = (string)($_POST['password'] ?? '');
        $login = api('POST', 'auth/token/', ['username' => $email, 'password' => $password]);
        $access = $login['data']['access'] ?? null;
        if ($login['status'] === 200 && is_string($access)) {
            $account = api('GET', 'auth/me/', null, $access);
            if ($account['status'] === 200 && !empty($account['data']['is_staff'])) {
                session_regenerate_id(true);
                $_SESSION['access'] = $access;
                $_SESSION['refresh'] = (string)($login['data']['refresh'] ?? '');
                $_SESSION['email'] = $email;
                csrfToken();
                flash('Signed in as an authorized catalog administrator.');
                redirectHome();
            }
            flash($account['status'] === 200 ? 'This account is not authorized to manage the catalog.' : apiError($account['data']), 'error');
        } else {
            flash($login['status'] === 0 ? 'Could not reach the Django API. Check its address and status.' : 'Sign-in failed. Check your credentials and try again.', 'error');
        }
        redirectHome();
    }

    if ($action === 'logout') {
        $_SESSION = [];
        if (ini_get('session.use_cookies')) {
            $params = session_get_cookie_params();
            setcookie(session_name(), '', time() - 42000, $params['path'], $params['domain'], $params['secure'], $params['httponly']);
        }
        session_destroy();
        redirectHome();
    }

    if (empty($_SESSION['access'])) {
        flash('Please sign in with an authorized administrator account.', 'error');
        redirectHome();
    }

    $type = in_array(($_POST['type'] ?? ''), ['games', 'compatibility', 'evidence'], true) ? (string)$_POST['type'] : 'gpus';
    $token = (string)$_SESSION['access'];
    if ($action === 'review_compatibility') {
        $id = rawurlencode((string)($_POST['id'] ?? ''));
        $decision = (string)($_POST['decision'] ?? '');
        if ($decision === 'approve') {
            $result = api('PATCH', 'compatibility/' . $id . '/', ['is_verified' => true], $token);
        } else {
            $result = api('PATCH', 'compatibility/' . $id . '/', ['is_rejected' => true], $token);
        }
        if ($result['status'] >= 200 && $result['status'] < 300) {
            flash($decision === 'approve' ? 'Compatibility suggestion approved.' : 'Compatibility suggestion dismissed.');
        } else {
            flash(apiError($result['data']), 'error');
        }
        redirectHome();
    }
    if ($action === 'delete') {
        $id = rawurlencode((string)($_POST['id'] ?? ''));
        $result = api('DELETE', $type . '/' . $id . '/', null, $token);
        if ($result['status'] === 401) {
            $_SESSION = [];
            flash('Your session expired. Sign in again.', 'error');
        } elseif ($result['status'] >= 200 && $result['status'] < 300) {
            flash('Catalog item deleted.');
        } else {
            flash(apiError($result['data']), 'error');
        }
        redirectHome();
    }

    if ($action === 'save') {
        $id = trim((string)($_POST['id'] ?? ''));
        if ($type === 'compatibility') {
            $body = [
                'gpu' => (int)($_POST['gpu'] ?? 0),
                'game' => (int)($_POST['game'] ?? 0),
                'technology' => (int)($_POST['technology'] ?? 0),
                'resolution' => (string)($_POST['resolution'] ?? ''),
                'status' => (string)($_POST['status'] ?? 'unknown'),
                'performance_summary' => trim((string)($_POST['performance_summary'] ?? '')),
                'image_quality_summary' => trim((string)($_POST['image_quality_summary'] ?? '')),
                'latency_summary' => trim((string)($_POST['latency_summary'] ?? '')),
                'limitations' => trim((string)($_POST['limitations'] ?? '')),
                'is_verified' => true,
            ];
        } elseif ($type === 'evidence') {
            $body = [
                'technology' => (int)($_POST['technology'] ?? 0),
                'game' => ($_POST['game'] ?? '') === '' ? null : (int)$_POST['game'],
                'gpu' => ($_POST['gpu'] ?? '') === '' ? null : (int)$_POST['gpu'],
                'resolution' => (string)($_POST['resolution'] ?? ''),
                'source_title' => trim((string)($_POST['source_title'] ?? '')),
                'source_url' => trim((string)($_POST['source_url'] ?? '')),
                'publisher' => trim((string)($_POST['publisher'] ?? '')),
                'published_at' => ($_POST['published_at'] ?? '') === '' ? null : (string)$_POST['published_at'],
                'findings' => trim((string)($_POST['findings'] ?? '')),
                'limitations' => trim((string)($_POST['limitations'] ?? '')),
                'preference' => (string)($_POST['preference'] ?? 'balanced'),
                'performance_score' => ($_POST['performance_score'] ?? '') === '' ? null : (int)$_POST['performance_score'],
                'image_quality_score' => ($_POST['image_quality_score'] ?? '') === '' ? null : (int)$_POST['image_quality_score'],
                'latency_score' => ($_POST['latency_score'] ?? '') === '' ? null : (int)$_POST['latency_score'],
                'confidence' => (int)($_POST['confidence'] ?? 3),
                'is_active' => isset($_POST['is_active']),
            ];
        } elseif ($type === 'games') {
            $body = [
                'name' => trim((string)($_POST['name'] ?? '')),
                'developer' => trim((string)($_POST['developer'] ?? '')),
                'engine' => trim((string)($_POST['engine'] ?? '')),
                'release_year' => ($_POST['release_year'] ?? '') === '' ? null : (int)$_POST['release_year'],
                'notes' => trim((string)($_POST['notes'] ?? '')),
                'is_active' => true,
            ];
        } else {
            $body = [
                'name' => trim((string)($_POST['name'] ?? '')),
                'manufacturer' => trim((string)($_POST['manufacturer'] ?? '')),
                'architecture' => trim((string)($_POST['architecture'] ?? '')),
                'notes' => trim((string)($_POST['notes'] ?? '')),
                'is_active' => true,
            ];
        }
        $result = api($id === '' ? 'POST' : 'PUT', $type . '/' . ($id === '' ? '' : rawurlencode($id) . '/'), $body, $token);
        if ($result['status'] === 401) {
            $_SESSION = [];
            flash('Your session expired. Sign in again.', 'error');
        } elseif ($result['status'] >= 200 && $result['status'] < 300) {
            $savedMessages = [
                'games' => 'Game saved.',
                'gpus' => 'Hardware saved.',
                'compatibility' => 'Compatibility record saved.',
                'evidence' => 'Research evidence saved.',
            ];
            flash($savedMessages[$type] ?? 'Record saved.');
        } else {
            flash(apiError($result['data']), 'error');
        }
        redirectHome();
    }
}

$message = $_SESSION['flash'] ?? null;
unset($_SESSION['flash']);
$editing = null;
$editType = ($_GET['type'] ?? '') === 'games' ? 'games' : 'gpus';
if (!empty($_GET['edit']) && !empty($_SESSION['access'])) {
    $item = api('GET', $editType . '/' . rawurlencode((string)$_GET['edit']) . '/', null, (string)$_SESSION['access']);
    if ($item['status'] === 200) $editing = $item['data'];
}

$gpus = $games = $technologies = $compatibility = $evidence = [];
$catalogError = '';
if (!empty($_SESSION['access'])) {
    foreach (['gpus' => 'gpus', 'games' => 'games', 'technologies' => 'technologies', 'compatibility' => 'compatibility', 'evidence' => 'evidence'] as $key => $endpoint) {
        $result = api('GET', $endpoint . '/', null, (string)$_SESSION['access']);
        if ($result['status'] === 401) {
            $_SESSION = [];
            $catalogError = 'Your administrator session expired. Sign in again.';
            break;
        }
        if ($result['status'] !== 200) {
            $catalogError = apiError($result['data']);
            break;
        }
        $rows = $result['data']['results'] ?? [];
        if (!is_array($rows)) $rows = [];
        if ($key === 'gpus') $gpus = $rows;
        elseif ($key === 'games') $games = $rows;
        elseif ($key === 'technologies') $technologies = $rows;
        elseif ($key === 'compatibility') $compatibility = $rows;
        else $evidence = $rows;
    }
}

function value(array|null $item, string $key, string $default = ''): string {
    return h($item[$key] ?? $default);
}
?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Graphics Advisor · Catalog Admin</title>
  <style>
    :root{font-family:Inter,ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;color:#e8edf5;background:#0c111b;font-synthesis:none}*{box-sizing:border-box}body{margin:0;background:radial-gradient(ellipse at 82% -20%,#1b3655 0,transparent 42%),#0c111b;min-height:100vh}.wrap{max-width:1120px;margin:0 auto;padding:32px 22px 64px}.top{display:flex;justify-content:space-between;align-items:center;gap:16px;border-bottom:1px solid #253142;padding:4px 0 22px}.brand{font-size:20px;font-weight:750;letter-spacing:-.03em}.brand span{color:#62d6b0}.muted{color:#97a5b8;font-size:14px}.layout{display:grid;grid-template-columns:minmax(290px,.8fr) minmax(0,1.5fr);gap:20px;margin-top:24px}.card{background:#121b28;border:1px solid #263548;border-radius:18px;padding:22px;box-shadow:0 18px 50px #0002}.card h2{font-size:18px;margin:0 0 18px}.login{max-width:440px;margin:70px auto}.field{display:grid;gap:7px;margin:14px 0}.field label{font-size:13px;color:#aeb9c9}.field input,.field textarea,.field select{width:100%;background:#0c131e;border:1px solid #34445a;border-radius:10px;color:#edf2f8;padding:11px 12px;font:inherit}.field textarea{min-height:78px;resize:vertical}.row{display:grid;grid-template-columns:1fr 1fr;gap:12px}.check{display:flex;align-items:center;gap:9px;color:#b7c2d1;margin:14px 0}.btn{border:0;background:#63d7af;color:#092018;padding:10px 15px;border-radius:10px;font-weight:750;cursor:pointer;text-decoration:none;display:inline-block}.btn.secondary{background:#26364b;color:#e7edf5}.btn.danger{background:#482631;color:#ffd8df}.btn.small{font-size:12px;padding:7px 10px}.actions{display:flex;gap:8px;align-items:center}.notice{padding:12px 15px;border-radius:11px;margin:18px 0;background:#17372f;color:#a4f4d4}.notice.error{background:#482631;color:#ffd8df}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size:14px}th,td{text-align:left;padding:12px 10px;border-bottom:1px solid #253142;vertical-align:top}th{color:#97a5b8;font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.05em}.tag{color:#a4f4d4;font-size:12px}.section{margin-top:18px}.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:8px}.section-head h3{margin:0;font-size:16px}.empty{padding:20px;color:#97a5b8}.api-note{font-size:12px;color:#8190a3;overflow-wrap:anywhere;margin-top:18px}@media(max-width:760px){.layout{grid-template-columns:1fr}.wrap{padding:22px 14px 44px}.top{align-items:flex-start}.row{grid-template-columns:1fr}}
  </style>
</head>
<body><main class="wrap">
  <header class="top"><div><div class="brand"><span>GA</span> Graphics Advisor</div><div class="muted">Catalog administration</div></div>
    <?php if (!empty($_SESSION['access'])): ?><form method="post"><input type="hidden" name="csrf" value="<?=h(csrfToken())?>"><input type="hidden" name="action" value="logout"><button class="btn secondary" type="submit">Sign out · <?=h($_SESSION['email'] ?? 'admin')?></button></form><?php endif; ?>
  </header>
  <?php if ($message): ?><div class="notice <?=h($message['type'] ?? 'success')?>"><?=h($message['message'] ?? '')?></div><?php endif; ?>
  <?php if (empty($_SESSION['access'])): ?>
    <section class="card login"><h2>Administrator sign in</h2><p class="muted">Use your Django account. Only accounts marked as staff can manage catalog entries.</p>
      <form method="post" autocomplete="on"><input type="hidden" name="csrf" value="<?=h(csrfToken())?>"><input type="hidden" name="action" value="login">
        <div class="field"><label for="email">Email or username</label><input id="email" name="email" type="text" required autocomplete="username"></div>
        <div class="field"><label for="password">Password</label><input id="password" name="password" type="password" required autocomplete="current-password"></div>
        <button class="btn" type="submit">Sign in</button>
      </form><div class="api-note">API: <?=h($apiBase)?></div>
    </section>
  <?php else: ?>
    <?php if ($catalogError !== ''): ?><div class="notice error"><?=h($catalogError)?></div><?php endif; ?>
    <div class="layout">
      <section class="card"><h2><?= $editing ? 'Edit' : 'Add' ?> <?= $editType === 'games' ? 'game' : 'hardware' ?></h2>
        <div class="actions"><a class="btn small <?= $editType === 'gpus' ? '' : 'secondary' ?>" href="?type=gpus">Hardware</a><a class="btn small <?= $editType === 'games' ? '' : 'secondary' ?>" href="?type=games">Games</a></div>
        <form method="post" autocomplete="off"><input type="hidden" name="csrf" value="<?=h(csrfToken())?>"><input type="hidden" name="action" value="save"><input type="hidden" name="type" value="<?=h($editType)?>"><input type="hidden" name="id" value="<?=value($editing, 'id')?>">
          <div class="field"><label for="name">Name</label><input id="name" name="name" maxlength="<?= $editType === 'games' ? '180' : '160' ?>" required value="<?=value($editing, 'name')?>"></div>
          <?php if ($editType === 'games'): ?>
            <div class="field"><label for="developer">Developer</label><input id="developer" name="developer" maxlength="160" value="<?=value($editing, 'developer')?>"></div>
            <div class="row"><div class="field"><label for="engine">Engine</label><input id="engine" name="engine" maxlength="120" value="<?=value($editing, 'engine')?>"></div><div class="field"><label for="release_year">Release year</label><input id="release_year" name="release_year" type="number" min="1970" max="2100" value="<?=value($editing, 'release_year')?>"></div></div>
          <?php else: ?>
            <div class="field"><label for="manufacturer">Manufacturer</label><input id="manufacturer" name="manufacturer" maxlength="80" required value="<?=value($editing, 'manufacturer')?>"></div>
            <div class="field"><label for="architecture">Architecture</label><input id="architecture" name="architecture" maxlength="120" value="<?=value($editing, 'architecture')?>"></div>
          <?php endif; ?>
          <div class="field"><label for="notes">Notes</label><textarea id="notes" name="notes"><?=value($editing, 'notes')?></textarea></div>
          <div class="actions"><button class="btn" type="submit">Save <?= $editType === 'games' ? 'game' : 'hardware' ?></button><?php if ($editing): ?><a class="btn secondary" href="?type=<?=h($editType)?>">Cancel</a><?php endif; ?></div>
        </form>
      </section>
      <section class="card"><h2>Catalog</h2>
        <?php foreach ([['Hardware', 'gpus', $gpus], ['Games', 'games', $games]] as [$label, $type, $items]): ?>
          <div class="section"><div class="section-head"><h3><?=h($label)?></h3><span class="tag"><?=count($items)?> entries</span></div>
            <?php if (!$items): ?><div class="empty">No entries found.</div><?php else: ?><div class="table-wrap"><table><thead><tr><th>Name</th><th>Details</th><th>Status</th><th>Actions</th></tr></thead><tbody>
              <?php foreach ($items as $item): ?><tr><td><strong><?=h($item['name'] ?? '')?></strong></td><td><?php if ($type === 'gpus'): ?><?=h($item['manufacturer'] ?? '')?><?php if (!empty($item['architecture'])): ?><br><span class="muted"><?=h($item['architecture'])?></span><?php endif; ?><?php else: ?><?=h($item['developer'] ?? '')?><?php if (!empty($item['release_year'])): ?><br><span class="muted"><?=h($item['release_year'])?></span><?php endif; ?><?php endif; ?></td><td><?=!empty($item['is_active']) ? '<span class="tag">Visible</span>' : '<span class="muted">Hidden</span>'?></td><td><div class="actions"><a class="btn secondary small" href="?type=<?=h($type)?>&amp;edit=<?=h($item['id'] ?? '')?>">Edit</a><form method="post" onsubmit="return confirm('Permanently delete this catalog item?')"><input type="hidden" name="csrf" value="<?=h(csrfToken())?>"><input type="hidden" name="action" value="delete"><input type="hidden" name="type" value="<?=h($type)?>"><input type="hidden" name="id" value="<?=h($item['id'] ?? '')?>"><button class="btn danger small" type="submit">Delete</button></form></div></td></tr><?php endforeach; ?>
            </tbody></table></div><?php endif; ?>
          </div>
        <?php endforeach; ?>
      </section>
    </div>
    <div class="layout">
      <section class="card"><h2>Add compatibility record</h2><p class="muted">Only mark a combination supported when verified. The GPU, game, and resolution must match the user's comparison.</p>
        <form method="post"><input type="hidden" name="csrf" value="<?=h(csrfToken())?>"><input type="hidden" name="action" value="save"><input type="hidden" name="type" value="compatibility">
          <div class="field"><label>GPU</label><select name="gpu" required><option value="">Choose hardware</option><?php foreach ($gpus as $gpu): ?><option value="<?=h($gpu['id'])?>"><?=h($gpu['name'])?></option><?php endforeach; ?></select></div>
          <div class="field"><label>Game</label><select name="game" required><option value="">Choose game</option><?php foreach ($games as $game): ?><option value="<?=h($game['id'])?>"><?=h($game['name'])?></option><?php endforeach; ?></select></div>
          <div class="row"><div class="field"><label>Technology</label><select name="technology" required><option value="">Choose technology</option><?php foreach ($technologies as $tech): ?><option value="<?=h($tech['id'])?>"><?=h($tech['name'])?></option><?php endforeach; ?></select></div><div class="field"><label>Resolution</label><select name="resolution"><option>1080p</option><option>1440p</option><option>2160p</option></select></div></div>
          <div class="field"><label>Status</label><select name="status"><option value="unknown">Unknown</option><option value="supported">Supported</option><option value="unsupported">Unsupported</option></select></div>
          <div class="field"><label>Performance notes</label><textarea name="performance_summary"></textarea></div><div class="field"><label>Image quality notes</label><textarea name="image_quality_summary"></textarea></div><div class="field"><label>Latency notes</label><textarea name="latency_summary"></textarea></div><div class="field"><label>Limitations / source context</label><textarea name="limitations"></textarea></div>
          <button class="btn" type="submit">Save compatibility</button>
        </form>
      </section>
      <section class="card"><h2>Add research evidence</h2><p class="muted">Cite the real source. Scores are 1–5; leave them blank if unsupported. An active matching scored record is required to rank compatible options.</p>
        <form method="post"><input type="hidden" name="csrf" value="<?=h(csrfToken())?>"><input type="hidden" name="action" value="save"><input type="hidden" name="type" value="evidence">
          <div class="field"><label>Technology</label><select name="technology" required><option value="">Choose technology</option><?php foreach ($technologies as $tech): ?><option value="<?=h($tech['id'])?>"><?=h($tech['name'])?></option><?php endforeach; ?></select></div>
          <div class="row"><div class="field"><label>GPU (optional)</label><select name="gpu"><option value="">Any / not specified</option><?php foreach ($gpus as $gpu): ?><option value="<?=h($gpu['id'])?>"><?=h($gpu['name'])?></option><?php endforeach; ?></select></div><div class="field"><label>Game (optional)</label><select name="game"><option value="">Any / not specified</option><?php foreach ($games as $game): ?><option value="<?=h($game['id'])?>"><?=h($game['name'])?></option><?php endforeach; ?></select></div></div>
          <div class="row"><div class="field"><label>Resolution</label><select name="resolution"><option value="">Any / not specified</option><option>1080p</option><option>1440p</option><option>2160p</option></select></div><div class="field"><label>Preference</label><select name="preference"><option value="balanced">Balanced</option><option value="performance">Performance</option><option value="image_quality">Image quality</option></select></div></div>
          <div class="field"><label>Source title</label><input name="source_title" maxlength="240" required></div><div class="row"><div class="field"><label>Source URL</label><input name="source_url" type="url"></div><div class="field"><label>Publisher</label><input name="publisher" maxlength="160"></div></div><div class="field"><label>Publication date</label><input name="published_at" type="date"></div>
          <div class="field"><label>Findings</label><textarea name="findings" required></textarea></div><div class="field"><label>Limitations</label><textarea name="limitations"></textarea></div>
          <div class="row"><div class="field"><label>Performance score (1–5)</label><input name="performance_score" type="number" min="1" max="5"></div><div class="field"><label>Image quality score (1–5)</label><input name="image_quality_score" type="number" min="1" max="5"></div></div><div class="row"><div class="field"><label>Latency score (1–5)</label><input name="latency_score" type="number" min="1" max="5"></div><div class="field"><label>Confidence (1–5)</label><input name="confidence" type="number" min="1" max="5" value="3"></div></div>
          <label class="check"><input type="checkbox" name="is_active" value="1" checked> Include in recommendations</label><button class="btn" type="submit">Save evidence</button>
        </form>
      </section>
    </div>
    <?php $suggestions = array_values(array_filter($compatibility, fn($row) => empty($row['is_verified']) && empty($row['is_rejected']))); ?>
    <section class="card section"><h2>Compatibility suggestions for review</h2>
      <p class="muted">Automatically gathered source entries are suggestions only. They do not affect recommendations until an administrator approves them. Check the citation and the GPU/resolution limitations before approval.</p>
      <?php if (!$suggestions): ?><div class="empty">No pending suggestions. Suggestions are generated when users request a comparison for a game/GPU pair.</div><?php else: ?><div class="table-wrap"><table><thead><tr><th>Game / Hardware</th><th>Technology</th><th>Resolution</th><th>Suggested status</th><th>Source notes</th><th>Review</th></tr></thead><tbody>
        <?php foreach ($suggestions as $row): ?><tr><td><strong><?=h($row['game_name'] ?? 'Game')?></strong><br><span class="muted"><?=h($row['gpu_name'] ?? 'GPU')?></span></td><td><?=h($row['technology_name'] ?? '')?></td><td><?=h($row['resolution'] ?? '')?></td><td><?=h($row['status'] ?? 'unknown')?></td><td><?=h($row['limitations'] ?? '')?><?php if (!empty($row['source_url'])): ?><br><a href="<?=h($row['source_url'])?>" target="_blank" rel="noopener noreferrer">Open source</a><?php endif; ?></td><td><div class="actions"><form method="post"><input type="hidden" name="csrf" value="<?=h(csrfToken())?>"><input type="hidden" name="action" value="review_compatibility"><input type="hidden" name="id" value="<?=h($row['id'] ?? '')?>"><input type="hidden" name="decision" value="approve"><button class="btn small" type="submit">Approve</button></form><form method="post"><input type="hidden" name="csrf" value="<?=h(csrfToken())?>"><input type="hidden" name="action" value="review_compatibility"><input type="hidden" name="id" value="<?=h($row['id'] ?? '')?>"><input type="hidden" name="decision" value="dismiss"><button class="btn danger small" type="submit">Dismiss</button></form></div></td></tr><?php endforeach; ?>
      </tbody></table></div><?php endif; ?>
    </section>
    <div class="card section"><h2>Recommendation data status</h2><p><?=count($compatibility)?> compatibility records (<?=count(array_filter($compatibility, fn($row) => ($row['status'] ?? '') === 'supported' && !empty($row['is_verified'])))?> verified supported), <?=count($suggestions)?> pending suggestions, <?=count($evidence)?> active evidence records.</p><p class="muted">Lists may be paginated. Verify each record matches the selected GPU, game, resolution, and preference.</p></div>
    <p class="api-note">Writes are submitted to Django, which checks staff authorization on every change. Removing a catalog entry can also remove its related compatibility and evidence records.</p>
  <?php endif; ?>
</main></body></html>
