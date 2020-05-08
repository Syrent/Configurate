/*
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spongepowered.configurate.hocon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.reflect.TypeToken;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.loader.AtomicFiles;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.configurate.objectmapping.Setting;
import org.spongepowered.configurate.serialize.ConfigSerializable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

/**
 * Basic sanity checks for the loader
 */
@ExtendWith(TempDirectory.class)
public class HoconConfigurationLoaderTest {

    @Test
    public void testSimpleLoading(@TempDirectory.TempDir Path tempDir) throws IOException {
        URL url = getClass().getResource("/example.conf");
        final Path saveTest = tempDir.resolve("text1.txt");

        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setSource(() -> new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)))
                .setSink(AtomicFiles.createAtomicWriterFactory(saveTest, StandardCharsets.UTF_8)).build();
        CommentedConfigurationNode node = loader.load();
        assertEquals("unicorn", node.getNode("test", "op-level").getValue());
        assertEquals("dragon", node.getNode("other", "op-level").getValue());
        CommentedConfigurationNode testNode = node.getNode("test");
        assertEquals(" Test node", testNode.getComment().orElse(null));
        assertEquals("dog park", node.getNode("other", "location").getValue());
        loader.save(node);
        assertEquals(Resources.readLines(getClass().getResource("/roundtrip-test.conf"), StandardCharsets.UTF_8), Files
                .readAllLines(saveTest, StandardCharsets.UTF_8));
    }

    @Test
    public void testSplitLineCommentInput(@TempDirectory.TempDir Path tempDir) throws IOException {
        final Path saveTo = tempDir.resolve("text2.txt");
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setPath(saveTo)
                .setURL(getClass().getResource("/splitline-comment-input.conf"))
                .build();
        CommentedConfigurationNode node = loader.load();
        loader.save(node);

        assertEquals(Resources.readLines(getClass().getResource("/splitline-comment-output.conf"), StandardCharsets.UTF_8), Files.readAllLines(saveTo, StandardCharsets.UTF_8));
    }

    @Test
    public void testHeaderSaved(@TempDirectory.TempDir Path tempDir) throws IOException {
        final Path saveTo = tempDir.resolve("text3.txt");
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setPath(saveTo)
                .build();
        CommentedConfigurationNode node = loader.createEmptyNode(ConfigurationOptions.defaults().withHeader("Hi! I am a header!\n" +
                        "Look at meeeeeee!!!"));
        node.getNode("node").setComment("I have a comment").getNode("party").setValue("now");

        loader.save(node);
        assertEquals(Resources.readLines(getClass().getResource("/header.conf"), StandardCharsets.UTF_8), Files.readAllLines(saveTo, StandardCharsets.UTF_8));

    }

    @Test
    public void testBooleansNotShared(@TempDirectory.TempDir Path tempDir) throws IOException {
        URL url = getClass().getResource("/comments-test.conf");
        final Path saveTo = tempDir.resolve("text4.txt");
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setPath(saveTo).setURL(url).build();

        CommentedConfigurationNode node = loader.createEmptyNode(ConfigurationOptions.defaults());
        node.getNode("test", "third").setValue(false).setComment("really?");
        node.getNode("test", "apple").setComment("fruit").setValue(false);
        node.getNode("test", "donut").setValue(true).setComment("tasty");
        node.getNode("test", "guacamole").setValue(true).setComment("and chips?");

        loader.save(node);
        assertEquals(Resources.readLines(url, StandardCharsets.UTF_8), Files.readAllLines(saveTo, StandardCharsets.UTF_8));
    }

    @Test
    public void testNewConfigObject() {
        Map<String, ConfigValue> entries = ImmutableMap.of("a", ConfigValueFactory.fromAnyRef("hi"), "b", ConfigValueFactory.fromAnyRef("bye"));
        HoconConfigurationLoader.newConfigObject(entries);
    }

    @Test
    public void testNewConfigList() {
        List<ConfigValue> entries = ImmutableList.of(ConfigValueFactory.fromAnyRef("hello"), ConfigValueFactory.fromAnyRef("goodbye"));
        HoconConfigurationLoader.newConfigList(entries);
    }

    @Test
    public void testRoundtripAndMergeEmpty(@TempDirectory.TempDir Path tempDir) throws IOException {
        // https://github.com/SpongePowered/Configurate/issues/44
        final URL rsrc = getClass().getResource("/empty-values.conf");
        final Path output = tempDir.resolve("load-merge-empty.conf");
        final HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setPath(output)
                .setURL(rsrc).build();

        CommentedConfigurationNode source = loader.load();
        CommentedConfigurationNode destination = loader.createEmptyNode();
        destination.mergeValuesFrom(source);
        loader.save(source);
        assertLinesMatch(Resources.readLines(rsrc, StandardCharsets.UTF_8), Files.readAllLines(output, StandardCharsets.UTF_8));
        loader.save(destination);
        assertLinesMatch(Resources.readLines(rsrc, StandardCharsets.UTF_8), Files.readAllLines(output, StandardCharsets.UTF_8));
    }

    static class OuterConfig {
        static TypeToken<OuterConfig> TYPE = TypeToken.of(OuterConfig.class);
        @Setting
        private Section section = new Section();
    }

    @ConfigSerializable
    static class Section {
        @Setting
        private Map<String, String> aliases = new HashMap<>();
    }

    @Test
    public void testCreateEmptyObjectmappingSection(@TempDirectory.TempDir Path tempDir) throws IOException, ObjectMappingException {
        // https://github.com/SpongePowered/Configurate/issues/40
        final URL rsrc = getClass().getResource("/empty-section.conf");
        final Path output = tempDir.resolve("empty-section.conf");
        final HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setPath(output)
                .setURL(rsrc).build();

        CommentedConfigurationNode source = loader.createEmptyNode();
        ObjectMapper.forType(OuterConfig.TYPE).bindToNew().populate(source);
        loader.save(source);
        assertLinesMatch(Resources.readLines(rsrc, StandardCharsets.UTF_8), Files.readAllLines(output, StandardCharsets.UTF_8));
    }
}
