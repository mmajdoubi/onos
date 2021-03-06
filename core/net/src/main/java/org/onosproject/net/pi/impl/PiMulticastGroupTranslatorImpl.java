/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.net.pi.impl;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions.OutputInstruction;
import org.onosproject.net.group.Group;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.pi.runtime.PiMulticastGroupEntry;
import org.onosproject.net.pi.runtime.PiPreReplica;
import org.onosproject.net.pi.service.PiTranslationException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * Implementation of multicast group translation logic.
 */
final class PiMulticastGroupTranslatorImpl {

    private PiMulticastGroupTranslatorImpl() {
        // Hides constructor.
    }

    /**
     * Returns a PI PRE multicast group entry equivalent to the given group, for
     * the given pipeconf and device.
     * <p>
     * The passed group is expected to have type {@link GroupDescription.Type#ALL}.
     *
     * @param group    group
     * @return PI PRE entry
     * @throws PiTranslationException if the group cannot be translated
     */
    static PiMulticastGroupEntry translate(Group group)
            throws PiTranslationException {

        checkNotNull(group);

        if (!group.type().equals(GroupDescription.Type.ALL)) {
            throw new PiTranslationException(format(
                    "group type %s not supported", group.type()));
        }

        final List<Instruction> instructions = group.buckets().buckets().stream()
                .flatMap(b -> b.treatment().allInstructions().stream())
                .collect(Collectors.toList());

        final boolean hasNonOutputInstr = instructions.stream()
                .anyMatch(i -> !i.type().equals(Instruction.Type.OUTPUT));

        if (instructions.size() != group.buckets().buckets().size()
                || hasNonOutputInstr) {
            throw new PiTranslationException(
                    "support only groups with just one OUTPUT instruction per bucket");
        }

        final List<OutputInstruction> outInstructions = instructions.stream()
                .map(i -> (OutputInstruction) i)
                .collect(Collectors.toList());

        return PiMulticastGroupEntry.builder()
                .withGroupId(group.id().id())
                .addReplicas(getReplicas(outInstructions))
                .build();
    }

    private static Set<PiPreReplica> getReplicas(Collection<OutputInstruction> instructions) {
        // Account for multiple replicas for the same port.
        final Map<PortNumber, Set<PiPreReplica>> replicaMap = Maps.newHashMap();
        final List<PortNumber> ports = instructions.stream()
                .map(OutputInstruction::port)
                .collect(Collectors.toList());
        for (PortNumber port : ports) {
            replicaMap.putIfAbsent(port, Sets.newHashSet());
            // Use incremental instance IDs for replicas of the same port.
            replicaMap.get(port).add(
                    new PiPreReplica(port, replicaMap.get(port).size() + 1));
        }
        return replicaMap.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }
}
