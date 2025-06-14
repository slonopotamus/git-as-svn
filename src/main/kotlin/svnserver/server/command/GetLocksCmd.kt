/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNException
import svnserver.parser.SvnServerWriter
import svnserver.repository.Depth
import svnserver.server.SessionContext
import java.io.IOException

/**
 * <pre>
 * get-locks
 * params:    ( path:string ? [ depth:word ] )
 * response   ( ( lock:lockdesc ... ) )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class GetLocksCmd : BaseCmd<GetLocksCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val locks = context.branch.repository.wrapLockRead { it.getLocks(context.user, context.branch, context.getRepositoryPath(args.path), args.depth) }
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listBegin()
        while (locks.hasNext()) LockCmd.writeLock(writer, locks.next())
        writer
            .listEnd()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkRead(context.getRepositoryPath(args.path))
    }

    class Params(val path: String, depth: Array<String>) {
        val depth: Depth = (if (depth.isEmpty()) null else Depth.parse(depth[0])) ?: Depth.Infinity
    }
}
