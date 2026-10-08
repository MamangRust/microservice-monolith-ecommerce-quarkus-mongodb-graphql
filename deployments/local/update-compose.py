#!/usr/bin/env python3
"""Rewrite deployments/local/docker-compose.yml for per-context MongoDB.

One database = one bounded context: six MongoDB instances (no proxy). Each
domain service depends only on its own context's `mongo_<context>` service.

The script is idempotent: it removes any existing postgres_*/pgbouncer_*/
postgres-exporter-*/mongo_*/mongodb-exporter-* service blocks, re-inserts the
six `mongo_<context>` blocks, and rewires every service's `depends_on` to its
context's instance.
"""
import re
import sys

COMPOSE = "deployments/local/docker-compose.yml"

# (context, mongo service name, db name, host port, [member service blocks])
CONTEXTS = [
    ("identity", "mongo_identity", "ec_identity", 27017,
     ["auth", "role", "user"]),
    ("merchant", "mongo_merchant", "ec_merchant", 27018,
     ["merchant", "merchant_award", "merchant_business", "merchant_detail", "merchant_policy"]),
    ("catalog", "mongo_catalog", "ec_catalog", 27019,
     ["category", "product"]),
    ("sales", "mongo_sales", "ec_sales", 27020,
     ["order", "order-item", "transaction"]),
    ("experience", "mongo_experience", "ec_experience", 27021,
     ["banner", "cart", "review", "review_detail", "shipping_address", "slider"]),
    ("email", "mongo_email", "ec_email", 27022,
     ["email"]),
]

HEADER = """  # 1 database = 1 MongoDB instance, tidak ada instance/database shared dan tidak
  # ada proxy (pooling di driver).
"""

MONGO_BLOCK = """  {svc}:
    image: mongo:7
    container_name: ecommerce-mongo-{ctx}
    command: ["mongod", "--wiredTigerCacheSizeGB", "1"]
    ports: ["{port}:27017"]
    volumes: ["{svc}_data:/data/db"]
    networks: [app_ecommerce_network]
    healthcheck:
      test: ["CMD-SHELL", "mongosh --quiet --eval 'db.adminCommand({{ ping: 1 }})' || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s

"""

TOP_LEVEL = re.compile(r"^  [a-zA-Z0-9_-]+:\s*$")
# Only the DB service blocks this script owns are removed on re-run; the
# mongodb-exporter services are managed elsewhere and left untouched.
DB_SERVICE = re.compile(r"^(postgres_\w+|pgbouncer_\w+|postgres-exporter-\w+|mongo_\w+)$")


def split_blocks(lines):
    """Split top-level service blocks. Returns list of (start, end, name|None)."""
    blocks = []
    i = 0
    while i < len(lines):
        line = lines[i].rstrip("\n")
        if TOP_LEVEL.match(line):
            start = i
            name = line.strip().rstrip(":").strip()
            i += 1
            while i < len(lines) and not TOP_LEVEL.match(lines[i].rstrip("\n")):
                i += 1
            blocks.append((start, i, name))
        else:
            i += 1
    return blocks


def find_block(lines, name):
    for start, end, n in split_blocks(lines):
        if n == name:
            return start, end
    return None


def main():
    with open(COMPOSE) as f:
        lines = f.readlines()

    # 1. Remove every existing DB service block (idempotent re-run).
    for _start, _end, name in reversed(split_blocks(lines)):
        if name and DB_SERVICE.match(name):
            del lines[_start:_end]

    # 1b. Drop a previously inserted header comment so re-runs don't duplicate it.
    lines = [l for l in lines if not l.strip().startswith(("# 1 database = 1 MongoDB instance",
                                                          "# ada proxy (pooling di driver)."))]

    # 2. Insert the six mongo blocks right after the `services:` line.
    services_idx = next(i for i, l in enumerate(lines) if l.startswith("services:"))
    blocks = HEADER + "".join(
        MONGO_BLOCK.format(ctx=ctx, svc=svc, db=dbname, port=port)
        for ctx, svc, dbname, port, _members in CONTEXTS
    )
    lines[services_idx + 1 : services_idx + 1] = blocks.splitlines(keepends=True)

    text = "".join(lines)

    # 3. For each service: drop stale DB depends_on entries, then point it at its
    #    own context instance.
    for _ctx, svc, _dbname, _port, members in CONTEXTS:
        for member in members:
            m = re.search(r"(^  %s:\n(?:.*\n)*?)(?=^  [a-zA-Z0-9_-]+:|\Z)" % re.escape(member), text, re.M)
            if not m:
                print(f"WARN: service block {member} not found", file=sys.stderr)
                continue
            block = m.group(1)
            # drop any DB service from depends_on
            block = re.sub(
                r"^      (?:postgres_\w+|pgbouncer_\w+|postgres-exporter-\w+|mongo_\w+|mongodb-exporter-\w+):\n        condition: service_healthy\n",
                "",
                block,
                flags=re.M,
            )
            # add own context instance right after `depends_on:`
            block = re.sub(
                r"(^    depends_on:\n)",
                r"\1      %s:\n        condition: service_healthy\n" % svc,
                block,
                count=1,
                flags=re.M,
            )
            text = text[: m.start()] + block + text[m.end() :]

    # 4. Add the mongo volumes to the volumes: section.
    volume_names = ["%s_data" % svc for _ctx, svc, _dbname, _port, _members in CONTEXTS]
    m = re.search(r"^(volumes:\n)", text, re.M)
    if m:
        existing = [v.strip() for v in text[m.end():].splitlines() if v.strip()]
        new_vols = [v for v in volume_names if v not in existing]
        insert = "".join("  %s:\n" % v for v in new_vols)
        text = text[: m.end()] + insert + text[m.end():]

    with open(COMPOSE, "w") as f:
        f.write(text)

    print("compose updated OK")


if __name__ == "__main__":
    main()
