/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.jitsi.jicofo;

import org.jitsi.jicofo.bridge.*;

import org.jitsi.jicofo.discovery.Version;
import org.jitsi.jicofo.jibri.JibriConfig;
import org.jitsi.jicofo.jigasi.*;
import org.jitsi.jicofo.recording.jibri.*;
import org.jitsi.jicofo.xmpp.*;
import org.jitsi.utils.logging.*;

import org.json.simple.*;
import org.jxmpp.jid.*;

import java.util.*;

/**
 * Class manages discovery of Jitsi Meet application services like
 * jitsi-videobridge, recording, SIP gateway and so on...
 *
 * @author Pawel Domas
 */
public class JitsiMeetServices
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(JitsiMeetServices.class);

    /**
     * Manages Jitsi Videobridge component XMPP addresses.
     */
    private final BridgeSelector bridgeSelector;

    private final Set<BaseBrewery> breweryDetectors = new HashSet<>();

    /**
     * The {@link ProtocolProviderHandler} for JVB XMPP connection.
     */
    private final ProtocolProviderHandler jvbBreweryProtocolProvider;

    /**
     * The {@link ProtocolProviderHandler} for Jicofo XMPP connection.
     */
    private final ProtocolProviderHandler protocolProvider;

    /**
     * Creates new instance of <tt>JitsiMeetServices</tt>
     *  @param protocolProviderHandler {@link ProtocolProviderHandler} for Jicofo XMPP connection.
     * @param jvbMucProtocolProvider {@link ProtocolProviderHandler} for JVB XMPP connection.
     */
    public JitsiMeetServices(ProtocolProviderHandler protocolProviderHandler,
                             ProtocolProviderHandler jvbMucProtocolProvider)
    {
        Objects.requireNonNull(protocolProviderHandler, "protocolProviderHandler");
        Objects.requireNonNull(jvbMucProtocolProvider, "jvbMucProtocolProvider");

        this.protocolProvider = protocolProviderHandler;
        this.jvbBreweryProtocolProvider = jvbMucProtocolProvider;
        this.bridgeSelector = new BridgeSelector();
    }

    /**
     * Returns Jibri SIP detector if available.
     * @return {@link JibriDetector} or <tt>null</tt> if not configured.
     */
    public JibriDetector getSipJibriDetector()
    {
        return breweryDetectors.stream()
            .filter(d -> d instanceof JibriDetector)
            .map(d -> ((JibriDetector) d))
            .filter(JibriDetector::isSip)
            .findFirst().orElse(null);
    }

    /**
     * Returns {@link JibriDetector} instance that manages Jibri pool used by
     * this Jicofo process or <tt>null</tt> if unavailable in the current
     * session.
     */
    public JibriDetector getJibriDetector()
    {
        return breweryDetectors.stream()
            .filter(d -> d instanceof JibriDetector)
            .map(d -> ((JibriDetector) d))
            .filter(d -> !d.isSip())
            .findFirst().orElse(null);
    }

    /**
     * Returns {@link JigasiDetector} instance that manages Jigasi pool used by
     * this Jicofo process or <tt>null</tt> if unavailable in the current
     * session.
     */
    public JigasiDetector getJigasiDetector()
    {
        return breweryDetectors.stream()
            .filter(d -> d instanceof JigasiDetector)
            .map(d -> ((JigasiDetector) d))
            .findFirst().orElse(null);
    }

    /**
     * Returns {@link BridgeSelector} bound to this instance that can be used to
     * select the videobridge on the xmppDomain handled by this instance.
     */
    public BridgeSelector getBridgeSelector()
    {
        return bridgeSelector;
    }

    public void start()
    {
        bridgeSelector.init();

        if (JibriConfig.config.breweryEnabled())
        {
            JibriDetector jibriDetector
                    = new JibriDetector(protocolProvider, JibriConfig.config.getBreweryJid(), false);
            logger.info("Using a Jibri detector with MUC: " + JibriConfig.config.getBreweryJid());

            jibriDetector.init();
            breweryDetectors.add(jibriDetector);
        }

        if (JigasiConfig.config.breweryEnabled())
        {
            JigasiDetector jigasiDetector = new JigasiDetector(protocolProvider, JigasiConfig.config.getBreweryJid());
            logger.info("Using a Jigasi detector with MUC: " + JigasiConfig.config.getBreweryJid());

            jigasiDetector.init();
            breweryDetectors.add(jigasiDetector);
        }

        if (JibriConfig.config.sipBreweryEnabled())
        {
            JibriDetector sipJibriDetector
                    = new JibriDetector(protocolProvider, JibriConfig.config.getSipBreweryJid(), true);
            logger.info("Using a SIP Jibri detector with MUC: " + JibriConfig.config.getSipBreweryJid());

            sipJibriDetector.init();
            breweryDetectors.add(sipJibriDetector);
        }

        if (BridgeConfig.config.breweryEnabled())
        {
            BridgeMucDetector bridgeMucDetector = new BridgeMucDetector(jvbBreweryProtocolProvider, bridgeSelector);
            bridgeMucDetector.init();
            breweryDetectors.add(bridgeMucDetector);
        }
    }

    public void stop()
    {
        breweryDetectors.forEach(BaseBrewery::dispose);
        breweryDetectors.clear();
        bridgeSelector.dispose();
    }

    @SuppressWarnings("unchecked")
    public JSONObject getStats()
    {
        JSONObject json = new JSONObject();

        json.put("bridge_selector", bridgeSelector.getStats());
        JigasiDetector jigasiDetector = getJigasiDetector();
        if (jigasiDetector != null)
        {
            json.put("jigasi_detector", jigasiDetector.getStats());
        }

        // TODO: remove once we migrate to the new names (see FocusManager.getStats() which puts the same stats under
        // the 'jibri' key.
        JibriDetector jibriDetector = getJibriDetector();
        if (jibriDetector != null)
        {
            json.put("jibri_detector", jibriDetector.getStats());
        }

        // TODO: remove once we migrate to the new names (see FocusManager.getStats() which puts the same stats under
        // the 'jibri' key.
        JibriDetector sipJibriDetector = getSipJibriDetector();
        if (sipJibriDetector != null)
        {
            json.put("sip_jibri_detector", sipJibriDetector.getStats());
        }

        return json;
    }
}
