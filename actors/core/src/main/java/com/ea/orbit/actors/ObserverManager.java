/*
 Copyright (C) 2015 Electronic Arts Inc.  All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1.  Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
 2.  Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
 3.  Neither the name of Electronic Arts, Inc. ("EA") nor the names of
     its contributors may be used to endorse or promote products derived
     from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY ELECTRONIC ARTS AND ITS CONTRIBUTORS "AS IS" AND ANY
 EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL ELECTRONIC ARTS OR ITS CONTRIBUTORS BE LIABLE FOR ANY
 DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.ea.orbit.actors;

import com.ea.orbit.actors.runtime.ActorReference;
import com.ea.orbit.concurrent.ConcurrentHashSet;
import com.ea.orbit.concurrent.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ObserverManager<T extends IActorObserver>
{
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ObserverManager.class);

    private ConcurrentHashSet<T> observers = new ConcurrentHashSet<>();

    public void addObserver(T observer)
    {
        if (observer == null)
        {
            throw new NullPointerException("Observer must not be null");
        }

        if (!(observer instanceof ActorReference))
        {
            throw new IllegalArgumentException("Was expecting a reference");
        }

        if (!observers.contains(observer))
        {
            observers.add(observer);
        }
    }

    /**
     * Pings all observers and removes the ones that no longer exist or that return an exception.
     * <p>
     * This can take a while as some requests may timeout if the node that was holding the observer has left the cluster.
     * </p>
     * <p>
     * The observer set can handle concurrent modifications.<br/>
     * So it is not necessary, nor recommended, to wait on the returned Task unless the application really needs.
     * </p>
     *
     * @return
     */
    public Task<?> cleanup()
    {
        final Stream<Task<?>> stream = observers.stream()
                .map(o ->
                        ((Task<?>) o.ping()).whenComplete((Object pr, Throwable pe) ->
                        {
                            if (pe != null)
                            {
                                // beware: this might run in parallel with other calls the actor.
                                // this shouldn't be a problem.
                                observers.remove(o);
                            }
                        }));
        return Task.allOf(stream);
    }

    public void notifyObservers(Consumer<T> callable)
    {
        List<T> fail = new ArrayList<>(0);
        observers.forEach(o -> {
            try
            {
                callable.accept(o);
            }
            catch (Exception ex)
            {
                fail.add(o);
                if (logger.isDebugEnabled())
                {
                    logger.debug("Removing observer due to exception", ex);
                }
            }
        });
        if (fail.size() > 0)
        {
            observers.removeAll(fail);
        }
    }

    public void clear()
    {
        observers.clear();
    }

    public void removeObserver(T observer)
    {
        observers.remove(observer);
    }

    public Stream<T> stream()
    {
        return observers.stream();
    }
}