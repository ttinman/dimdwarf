// Copyright © 2008-2009, Esko Luontola. All Rights Reserved.
// This software is released under the MIT License.
// The license may be viewed at http://dimdwarf.sourceforge.net/LICENSE

package net.orfjackal.dimdwarf.scheduler;

import javax.annotation.CheckForNull;
import java.util.concurrent.TimeUnit;

/**
 * @author Esko Luontola
 * @since 25.11.2008
 */
public interface SchedulingStrategy {

    @CheckForNull
    SchedulingStrategy nextRepeatedRun();

    long getScheduledTime();

    long getDelay(TimeUnit unit);
}