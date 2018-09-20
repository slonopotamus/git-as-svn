/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.context.LocalContext;
import svnserver.context.Shared;

/**
 * GIT LFS storage factory for Local context.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface LfsStorageFactory extends Shared {

  @Nullable
  static LfsStorage tryCreateStorage(@NotNull LocalContext context) {
    final LfsStorageFactory storageFactory = context.getShared().get(LfsStorageFactory.class);
    return storageFactory == null ? null : storageFactory.createStorage(context);
  }

  @NotNull
  LfsStorage createStorage(@NotNull LocalContext context);
}
