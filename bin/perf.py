"""
49e76b6: 0.5760772399999999
b219a5e: 0.5664049800000002
"""
import requests

req = """query {{
  books(
    authorKeywords: {author_kw},
    titleKeywords: {title_kw},
    maxResults: 30
  ) {{
    authors title description isbn
  }}
}}
"""

SEARCHES = [
    ("tolkien", "lord"),
    ("tolkien", None),
    ("Gene Wolfe", None),
    ("sanderson", None),
    (None, "Emacs"),
    (None, "Python"),
    ("Dan Simmons", None),
]


def perf_test(iterations=10, searches=SEARCHES, body_skeleton=req):
    total_time = 0
    for i in range(iterations):
        author_kw, title_kw = searches[i % len(searches)]
        body = body_skeleton.format(
            author_kw="null" if author_kw is None else "\"" + author_kw + "\"",
            title_kw="null" if title_kw is None else "\"" + title_kw + "\""
        )
        print(body)
        response = requests.post(
            "http://localhost:56848/api/graphql",
            json={"query": body},
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json"
            }
        )
        total_time += response.elapsed.total_seconds()
    return total_time / iterations
