"""Expose the latest completed production workflow from GitHub Actions."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen

RUNS_URL = (
    "https://api.github.com/repos/taller-web-sigesu/backend-uni/actions/workflows/"
    "deploy-production.yml/runs?branch=production&status=completed&per_page=1"
)
FAILED = {"failure", "timed_out", "startup_failure", "action_required"}


def exposition(payload):
    runs = payload["workflow_runs"]
    if not runs:
        return b'gestal_deployment_workflow_observed 0\n'

    run = runs[0]
    if run["status"] != "completed" or run["head_branch"] != "production":
        raise ValueError("Unexpected Actions response")

    conclusion = run["conclusion"]
    # A cancellation or a skipped run has an unknown deployment outcome.
    metrics = "gestal_deployment_workflow_observed 1\n"
    if conclusion in FAILED or conclusion == "success":
        metrics += f"gestal_deployment_workflow_failed {int(conclusion in FAILED)}\n"
    metrics += f"gestal_deployment_workflow_run_id {int(run['id'])}\n"
    return metrics.encode("ascii")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/metrics":
            self.send_error(404)
            return

        request = Request(
            RUNS_URL,
            headers={
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "Gestal-observability",
            },
        )
        try:
            with urlopen(request, timeout=5) as response:
                metrics = exposition(json.load(response))
        except (OSError, ValueError, KeyError, TypeError):
            self.send_error(503, "GitHub Actions data unavailable")
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
        self.end_headers()
        self.wfile.write(metrics)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 9101), Handler).serve_forever()
