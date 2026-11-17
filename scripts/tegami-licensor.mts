// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz

import { appendFile, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { WorkspacePackage } from "tegami";
import type { PackagePublishTaskResult, PublishTaskRunContext, TegamiPlugin } from "tegami";
import { GitTagPublishTask } from "tegami/plugins/git";

const RELEASE_DIR = "releases/licensor";
const ACTION_FILE = ".github/actions/setup-licensor/action.yml";
const ACTION_DEFAULT = /(\n {2}version:\n(?: {4}.*\n)*? {4}default: )v\S+/;
const SEMVER = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/;

class LicensorPackage extends WorkspacePackage {
  readonly manager = "release";
  readonly name = "licensor";
  readonly path: string;
  private current: string;

  constructor(dir: string, version: string) {
    super();
    this.path = dir;
    this.current = version;
  }

  get version(): string {
    return this.current;
  }

  async write(version: string, root: string): Promise<void> {
    this.current = version;
    await writeFile(path.join(this.path, "VERSION"), `${version}\n`);

    const action = path.join(root, ACTION_FILE);
    const content = await readFile(action, "utf8");
    if (!ACTION_DEFAULT.test(content)) throw new Error(`[licensor] no version default in ${ACTION_FILE}`);
    await writeFile(action, content.replace(ACTION_DEFAULT, `$1v${version}`));
  }
}

class LicensorPublishTask extends GitTagPublishTask<LicensorPackage> {
  async publish(opts: PublishTaskRunContext): Promise<PackagePublishTaskResult> {
    const result = await super.publish(opts);
    const output = process.env.GITHUB_OUTPUT;
    if (result.type === "published" && output) await appendFile(output, `tag=v${this.pkg.version}\n`);
    return result;
  }
}

export function licensor(): TegamiPlugin {
  return {
    name: "licensor",
    async resolve() {
      const dir = path.resolve(this.cwd, RELEASE_DIR);
      const version = (await readFile(path.join(dir, "VERSION"), "utf8")).trim();
      if (!SEMVER.test(version)) throw new Error(`[licensor] ${RELEASE_DIR}/VERSION is not a valid version: "${version}"`);
      this.graph.add(new LicensorPackage(dir, version));
    },
    async applyDraft(draft) {
      for (const pkg of this.graph.getPackages()) {
        if (!(pkg instanceof LicensorPackage)) continue;
        const bumped = draft.getPackageDraft(pkg.id)?.bumpVersion(pkg);
        if (bumped && bumped !== pkg.version) await pkg.write(bumped, this.cwd);
      }
    },
    initPublishPlan({ plan }) {
      for (const [id, packagePlan] of plan.packages) {
        const pkg = this.graph.get(id);
        if (pkg instanceof LicensorPackage) (packagePlan.git ??= {}).tag = `v${pkg.version}`;
      }
    },
    publishPreflight({ pkg }) {
      if (pkg instanceof LicensorPackage) return { shouldPublish: true };
    },
    publishTasks({ plan }) {
      return plan.getPackagesToPublish().map((pkg) => pkg instanceof LicensorPackage && new LicensorPublishTask(pkg));
    },
  };
}
