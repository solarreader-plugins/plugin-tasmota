/*
 * Copyright (c) 2024-2025 Stefan Toengi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.schnippsche.solarreader.test;

import de.schnippsche.solarreader.backend.command.Command;
import de.schnippsche.solarreader.backend.command.SendCommand;
import de.schnippsche.solarreader.backend.connection.general.ConnectionFactory;
import de.schnippsche.solarreader.backend.connection.network.HttpConnection;
import de.schnippsche.solarreader.database.ProviderData;
import de.schnippsche.solarreader.frontend.ui.ValueText;
import de.schnippsche.solarreader.plugins.tasmota.Tasmota;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tinylog.Logger;

class TasmotaTest {

  @Test
  void test() throws Exception {
    GeneralTestHelper generalTestHelper = new GeneralTestHelper();
    ConnectionFactory<HttpConnection> testFactory =
        knownConfiguration -> new TasmotaHttpConnection();
    Tasmota provider = new Tasmota(testFactory);
    ProviderData providerData = new ProviderData();
    providerData.setSetting(provider.getDefaultProviderSetting());
    providerData.setPluginName("Tasmota");
    providerData.setName("Tasmota Test");
    provider.setProviderData(providerData);
    generalTestHelper.testProviderInterface(provider);
    List<Command> commands = providerData.getAvailableCommands();
    for (Command command : commands) {
      Logger.debug(command.getTitleLanguageKey());
      for (ValueText valueText : command.getOptions()) {
        Logger.debug(valueText.getText());
        String selected = valueText.getValue();
        SendCommand sendCommand = new SendCommand(command, selected);
        provider.sendCommand(sendCommand);
      }
    }
  }
}
