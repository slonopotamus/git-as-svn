/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository

import java.io.IOException

/**
 * Consumer with VCS exceptions.
 *
 * @author a.navrotskiy
 */
fun interface VcsSupplier<T> {
    @Throws(IOException::class)
    fun get(): T
}
