// SPDX-License-Identifier: MIT
// Copyright: 2026 NiceBots.xyz

import { tegami } from "tegami";
import { runCli } from "tegami/cli";
import { github } from "tegami/plugins/github";
import { licensor } from "./tegami-licensor.mts";

const versionTitle = "chore(release): version packages";

const paper = tegami({
  npm: { updateLockFile: false },
  plugins: [
    licensor(),
    github({
      release: false,
      versionPr: {
        base: "master",
        create: () => ({ title: versionTitle }),
        commit: ({ type }) => (type === "version-packages" ? { title: versionTitle } : undefined),
      },
    }),
  ],
});

await runCli(paper);
