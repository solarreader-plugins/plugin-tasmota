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
package de.schnippsche.solarreader.plugins.tasmota;

import static de.schnippsche.solarreader.backend.util.Setting.OPTIONAL_PASSWORD;
import static de.schnippsche.solarreader.backend.util.Setting.OPTIONAL_USER;
import static de.schnippsche.solarreader.backend.util.Setting.PROVIDER_HOST;

import de.schnippsche.solarreader.backend.calculator.MapCalculator;
import de.schnippsche.solarreader.backend.command.Command;
import de.schnippsche.solarreader.backend.command.SendCommand;
import de.schnippsche.solarreader.backend.connection.general.ConnectionFactory;
import de.schnippsche.solarreader.backend.connection.network.HttpConnection;
import de.schnippsche.solarreader.backend.connection.network.HttpConnectionFactory;
import de.schnippsche.solarreader.backend.field.PropertyField;
import de.schnippsche.solarreader.backend.protocol.KnownProtocol;
import de.schnippsche.solarreader.backend.provider.AbstractHttpProvider;
import de.schnippsche.solarreader.backend.provider.CommandProviderProperty;
import de.schnippsche.solarreader.backend.provider.ProviderProperty;
import de.schnippsche.solarreader.backend.provider.SupportedInterface;
import de.schnippsche.solarreader.backend.table.Table;
import de.schnippsche.solarreader.backend.util.JsonTools;
import de.schnippsche.solarreader.backend.util.Setting;
import de.schnippsche.solarreader.backend.util.StringConverter;
import de.schnippsche.solarreader.database.Activity;
import de.schnippsche.solarreader.frontend.ui.HtmlInputType;
import de.schnippsche.solarreader.frontend.ui.HtmlWidth;
import de.schnippsche.solarreader.frontend.ui.UIInputElementBuilder;
import de.schnippsche.solarreader.frontend.ui.UIList;
import de.schnippsche.solarreader.frontend.ui.UITextElementBuilder;
import de.schnippsche.solarreader.frontend.ui.ValueText;
import de.schnippsche.solarreader.plugin.PluginMetadata;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tinylog.Logger;

/**
 * Represents a provider for interacting with Tasmota devices over HTTP. This class extends {@link
 * AbstractHttpProvider} and is responsible for sending and receiving HTTP requests to and from
 * Tasmota devices.
 *
 * <p>Tasmota is a popular open-source firmware for smart devices, and this class provides methods
 * to interact with such devices by making HTTP requests. It may include functionality for querying
 * device status, sending commands, or configuring the device settings over the HTTP protocol.
 */
@PluginMetadata(
    name = "Tasmota",
    version = "1.0.1",
    author = "Stefan Töngi",
    url = "https://github.com/solarreader-plugins/plugin-Tasmota",
    svgImage = "tasmota.svg",
    supportedInterfaces = {SupportedInterface.NONE},
    usedProtocol = KnownProtocol.HTTP,
    supports = "Tasmota Firmware")
public class Tasmota extends AbstractHttpProvider {
  private static final String BASE_URL = "http://{provider_host}";
  private static final String DEFAULT_COMMAND = "/cm?cmnd=Status0";
  private static final String ON = "on";
  private static final String OFF = "off";
  private static final String TOGGLE = "toggle";
  private List<Command> availableCommandList;

  /**
   * Constructs a new instance of the {@link Tasmota} class with the default HTTP connection
   * factory.
   *
   * <p>This constructor uses the default {@link HttpConnectionFactory} to manage HTTP connections.
   * It initializes the list of available commands and logs the instantiation of the object.
   */
  public Tasmota() {
    this(new HttpConnectionFactory());
  }

  /**
   * Constructs a new instance of the {@link Tasmota} class with the default HTTP connection factory
   * or a specified connection factory for managing HTTP connections.
   *
   * <p>The first constructor initializes the connection factory using the default {@link
   * HttpConnectionFactory}. The second constructor allows the user to provide a custom {@link
   * ConnectionFactory} to manage HTTP connections to the Tasmota device. It also initializes an
   * empty list of available commands and logs the instantiation of the object.
   *
   * @param connectionFactory the {@link ConnectionFactory} used to create and manage HTTP
   *     connections for interacting with the Tasmota device. If {@code null}, a default {@link
   *     HttpConnectionFactory} will be used.
   */
  public Tasmota(ConnectionFactory<HttpConnection> connectionFactory) {
    super(connectionFactory);
    this.availableCommandList = new ArrayList<>();
    Logger.debug("instantiate {}", this.getClass().getName());
  }

  /**
   * Retrieves the resource bundle for the plugin based on the specified locale.
   *
   * <p>This method overrides the default implementation to return a {@link ResourceBundle} for the
   * plugin using the provided locale.
   *
   * @return The {@link ResourceBundle} for the plugin, localized according to the specified locale.
   */
  @Override
  public ResourceBundle getPluginResourceBundle() {
    return ResourceBundle.getBundle("tasmota", locale);
  }

  @Override
  public Activity getDefaultActivity() {
    return new Activity(LocalTime.of(0, 0, 0), LocalTime.of(23, 59, 59), 1, TimeUnit.MINUTES);
  }

  @Override
  public Optional<UIList> getProviderDialog() {
    UIList uiList = new UIList();
    uiList.addElement(
        new UITextElementBuilder().withLabel(resourceBundle.getString("tasmota.title")).build());
    uiList.addElement(
        new UIInputElementBuilder()
            .withName(PROVIDER_HOST)
            .withType(HtmlInputType.TEXT)
            .withColumnWidth(HtmlWidth.FULL)
            .withRequired(true)
            .withTooltip(resourceBundle.getString("tasmota.host.tooltip"))
            .withLabel(resourceBundle.getString("tasmota.host.text"))
            .withPlaceholder(resourceBundle.getString("tasmota.host.text"))
            .withInvalidFeedback(resourceBundle.getString("tasmota.required.error"))
            .build());
    uiList.addElement(
        new UIInputElementBuilder()
            .withName(OPTIONAL_USER)
            .withType(HtmlInputType.TEXT)
            .withColumnWidth(HtmlWidth.HALF)
            .withRequired(false)
            .withTooltip(resourceBundle.getString("tasmota.user.tooltip"))
            .withLabel(resourceBundle.getString("tasmota.user.text"))
            .withPlaceholder(resourceBundle.getString("tasmota.user.text"))
            .build());
    uiList.addElement(
        new UIInputElementBuilder()
            .withName(OPTIONAL_PASSWORD)
            .withType(HtmlInputType.TEXT)
            .withColumnWidth(HtmlWidth.HALF)
            .withRequired(false)
            .withTooltip(resourceBundle.getString("tasmota.password.tooltip"))
            .withLabel(resourceBundle.getString("tasmota.password.text"))
            .withPlaceholder(resourceBundle.getString("tasmota.password.text"))
            .build());

    return Optional.of(uiList);
  }

  @Override
  public Optional<List<ProviderProperty>> getSupportedProperties() {
    if (providerData != null && providerData.getProviderProperties() != null) {
      return Optional.of(providerData.getProviderProperties());
    }
    return Optional.empty();
  }

  @Override
  public Optional<List<Table>> getDefaultTables() {
    return Optional.empty();
  }

  @Override
  public Setting getDefaultProviderSetting() {
    Setting setting = new Setting();
    setting.setProviderHost("localhost");
    setting.setProviderPort(80);
    return setting;
  }

  @Override
  public String testProviderConnection(Setting testSetting)
      throws IOException, InterruptedException {
    URL testUrl = getUrl(testSetting, BASE_URL + DEFAULT_COMMAND);
    Logger.debug("test url {}", testUrl);
    HttpConnection testConnection = connectionFactory.createConnection(testSetting);
    testConnection.test(testUrl, HttpConnection.CONTENT_TYPE_JSON);
    String json = testConnection.getAsString(testUrl);
    Logger.debug("result is {}", json);
    Map<String, Object> result = new JsonTools().getSimpleMapFromJsonString(json);
    String name = String.valueOf(result.getOrDefault("Status_DeviceName", ""));
    String topic = String.valueOf(result.getOrDefault("Status_Topic", ""));
    String message = resourceBundle.getString("tasmota.connection.successful");
    String returnCode = MessageFormat.format(message, name, topic);
    Logger.debug("returns code {}", returnCode);
    return returnCode;
  }

  @Override
  public void doOnFirstRun() throws IOException, InterruptedException {
    if (providerData.getProviderProperties() == null) {
      List<ProviderProperty> providerProperties = new ArrayList<>();
      CommandProviderProperty commandProviderProperty = new CommandProviderProperty();
      commandProviderProperty.setName("cmd");
      String urlPattern = BASE_URL + DEFAULT_COMMAND;
      commandProviderProperty.setCommand(urlPattern);
      URL url = getUrl(providerData.getSetting(), urlPattern);
      List<PropertyField> allSupportedFields = new ArrayList<>(getAvailableFields(url));
      commandProviderProperty.getPropertyFieldList().addAll(allSupportedFields);
      providerProperties.add(commandProviderProperty);
      providerData.setProviderProperties(providerProperties);
      providerData.setProviderPropertiesChanged(true);
      analyzeAvailableCommands(allSupportedFields);
    }
    if (providerData.getTableList() == null) {
      providerData.setTableList(new ArrayList<>());
      providerData.setTablesChanged(true);
    }
  }

  @Override
  public boolean doActivityWork(Map<String, Object> variables)
      throws IOException, InterruptedException {
    HttpConnection httpConnection = getConnection();
    workProperties(httpConnection, variables);
    return true;
  }

  @Override
  public void sendCommand(SendCommand sendCommand) throws IOException, InterruptedException {
    String urlPattern = String.format("%s/cm?%s", BASE_URL, sendCommand.getSend());
    URL url = getUrl(providerData.getSetting(), urlPattern);
    Logger.debug("send action url {}", url);
    HttpConnection httpConnection = getConnection();
    httpConnection.get(url);
  }

  @Override
  public List<Command> getAvailableCommands() {
    return availableCommandList;
  }

  private Set<PropertyField> getAvailableFields(URL url) throws IOException, InterruptedException {
    String json = getConnection().getAsString(url);
    return new JsonTools().getAvailableFieldsFromJson(json, "cmd");
  }

  private URL getUrl(Setting setting, String urlPattern) throws IOException {
    Map<String, String> configurationValues = setting.getConfigurationValues();
    String urlString =
        new StringConverter(urlPattern).replaceNamedPlaceholders(configurationValues);
    return new StringConverter(urlString).toUrl();
  }

  private void analyzeAvailableCommands(List<PropertyField> providerFields) {
    Pattern pattern = Pattern.compile("StatusSTS_POWER(\\d*)");
    Set<Command> commandSet = new HashSet<>();
    for (PropertyField field : providerFields) {
      Matcher matcher = pattern.matcher(field.getFieldName());
      if (matcher.find()) {
        int index = new StringConverter(matcher.group(1)).toInt(-1);
        String internal = "cmnd=Power";
        String label = "tasmota.relay.single";
        if (index != -1) {
          label = "tasmota.relay.multi";
          internal += (index + 1);
        }
        internal = internal + "%20";
        List<ValueText> optionList =
            List.of(
                new ValueText(internal + ON, "tasmota.option.on"),
                new ValueText(internal + OFF, "tasmota.option.off"),
                new ValueText(internal + TOGGLE, "tasmota.option.toggle"));
        commandSet.add(new Command("Tasmota", label, optionList, null, index + 1));
      }
    }
    availableCommandList = new ArrayList<>(commandSet);
    Collections.sort(availableCommandList);
    providerData.setAvailableCommands(availableCommandList);
  }

  @Override
  protected void handleCommandProperty(
      HttpConnection httpConnection,
      CommandProviderProperty commandProviderProperty,
      Map<String, Object> variables)
      throws IOException, InterruptedException {
    String url = commandProviderProperty.getCommand();
    Logger.debug("read from url {}...", url);
    Map<String, Object> values =
        new JsonTools()
            .getSimpleMapFromJsonString(
                httpConnection.getAsString(getUrl(providerData.getSetting(), url)));
    new MapCalculator()
        .calculate(values, commandProviderProperty.getPropertyFieldList(), variables);
  }
}
