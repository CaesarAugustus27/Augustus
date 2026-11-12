(async function trashbin() {
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

    // Spicetify wystawia swoje API dopiero po załadowaniu klienta
    const API = ["LocalStorage", "React", "ReactDOM", "ContextMenu", "Menu", "PopupModal"];
    while (!Spicetify.Player?.data || API.some((name) => !Spicetify[name])) {
        await sleep(500);
    }

    const {React, ReactDOM} = Spicetify;

    const AUGUSTUS = "http://127.0.0.1:8080/api/spotify/trash";
    const TRASH_KEYS = {track: "TrashSongList", artist: "TrashArtistList"};
    const WIDGET_KEY = "TrashbinWidgetIcon";
    const PENDING_REMOVALS_KEY = "TrashbinAugustusRemovals";
    const CHANGE_EVENT = "trashbinChange";
    const SKIP_BACK_SELECTOR = "[data-testid='control-button-skip-back'], .main-skipBackButton-button";
    const BACK_WINDOW_MS = 3000;
    const ADD_LABEL = "Wrzuć do kosza";
    const REMOVE_LABEL = "Wyjmij z kosza";
    // Ikonka kosza z klienta Spotify (moduł ikon Encore w xpui-modules.js, wariant 16 px)
    const TRASH_ICON =
        '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="currentcolor"><path d="M5.25 3v-.917C5.25.933 6.183 0 7.333 0h1.334c1.15 0 2.083.933 2.083 2.083V3h4.75v1.5h-.972l-1.257 9.544A2.25 2.25 0 0 1 11.041 16H4.96a2.25 2.25 0 0 1-2.23-1.956L1.472 4.5H.5V3zm1.5-.917V3h2.5v-.917a.583.583 0 0 0-.583-.583H7.333a.583.583 0 0 0-.583.583M2.986 4.5l1.23 9.348a.75.75 0 0 0 .744.652h6.08a.75.75 0 0 0 .744-.652L13.015 4.5H2.985z"/></svg>';

    //-------------------------------------------------------Stan-------------------------------------------------------
    // Kosz to dwie mapy URI → true, pod typem z URI (spotify:track:…, spotify:artist:…). Tytuły, albumy i nazwy
    // wykonawców są w Augustusie.

    const trash = {track: read(TRASH_KEYS.track, {}), artist: read(TRASH_KEYS.artist, {})};
    let showWidget = read(WIDGET_KEY, true);
    let pendingRemovals = read(PENDING_REMOVALS_KEY, []);
    let backPressedAt = 0;

    function read(key, fallback) {
        try {
            return JSON.parse(Spicetify.LocalStorage.get(key)) ?? fallback;
        } catch {
            return fallback;
        }
    }

    // Zapisuje kosz, odświeża przyciski kosza (słuchają CHANGE_EVENT) i wysyła kosz do Augustusa
    function storeTrash() {
        try {
            for (const kind of ["track", "artist"]) Spicetify.LocalStorage.set(TRASH_KEYS[kind], JSON.stringify(trash[kind]));
        } catch (error) {
            Spicetify.showNotification("Trashbin: nie udało się zapisać kosza w LocalStorage", true);
            console.error(error);
        }
        document.dispatchEvent(new CustomEvent(CHANGE_EVENT));
        syncToAugustus();
    }

    //-------------------------------------------------------Kosz-------------------------------------------------------

    const isTrackUri = (value) => typeof value === "string" && value.startsWith("spotify:track:");

    // Mapa kosza dla URI utworu albo wykonawcy; innych URI (album, odcinek podcastu, plik lokalny) kosz nie przyjmuje
    function listOf(uri) {
        const kind = typeof uri === "string" ? uri.split(":")[1] : null;
        return kind === "track" || kind === "artist" ? trash[kind] : null;
    }

    // Metadane grającego utworu trzymają wykonawców pod artist_uri, artist_uri:1, artist_uri:2, …
    const ARTIST_KEY = /^artist_uri(:\d+)?$/;
    const artistsOf = (item) => Object.entries(item?.metadata ?? {}).filter(([key]) => ARTIST_KEY.test(key)).map(([, uri]) => uri);

    const isTrashed = (item) => !!trash.track[item.uri] || artistsOf(item).some((uri) => trash.artist[uri]);

    function isPlaying(uri) {
        const item = Spicetify.Player.data?.item;
        return !!item && (item.uri === uri || artistsOf(item).includes(uri));
    }

    function toggle(uri) {
        const list = listOf(uri);
        if (!list) return;
        const what = isTrackUri(uri) ? "Utwór" : "Wykonawca";

        if (list[uri]) {
            delete list[uri];
            queueRemoval(uri);
            Spicetify.showNotification(`${what} wyjęty z kosza`);
        } else {
            list[uri] = true;
            cancelRemoval(uri);
            Spicetify.showNotification(`${what} w koszu, będzie pomijany`);
        }
        storeTrash();
        if (list[uri] && isPlaying(uri)) skipTrashed(Spicetify.Player.data.item).catch(console.error);
    }

    //----------------------------------------------------Pomijanie-----------------------------------------------------
    //
    // Utwór z kosza przeskakujemy jednym skokiem do pierwszego spoza kosza w kolejce klienta. Kolejka to okno
    // najwyżej 80 utworów, kończy się z końcem kontekstu (playlisty, albumu), przy powtarzaniu zwykle też, i dogania
    // songchange po ułamku sekundy. Dlatego:
    // - dopóki nie dogoni, pokazuje stan sprzed zmiany; jeśli grający utwór jest w niej jeszcze przed nami, a za nim
    //   coś spoza kosza, skaczemy od razu, a na resztę decyzji czekamy na kolejkę, która go już ma jako bieżący;
    // - w oknie same utwory z kosza: skok na ostatni z nich i dalej przy jego songchange;
    // - puste okno: bez powtarzania to koniec, więc pauza; z powtarzaniem next(), które zawija na początek;
    // - utwór już minięty w tym ciągu skoków to pełne okrążenie, więc też pauza.

    const QUEUE_WAIT_MS = 3000;
    let skipRun = 0; // numer zmiany; skok liczony na starej kolejce nie może przebić nowszej zmiany utworu
    let passed = new Set(); // utwory z kosza minięte od ostatniego utworu spoza kosza
    let passedContext = null;

    // uid odróżnia powtórzenia tego samego utworu w kontekście; utwór bez uid rozpoznajemy po URI
    const trackKey = (track) => track.uid || track.uri;

    function onSongChange() {
        syncPlaybarButton();

        const context = Spicetify.Player.data?.context?.uri ?? null;
        const newContext = context !== passedContext;
        if (newContext) {
            passed = new Set();
            passedContext = context;
        }

        // Powrót przyciskiem „Poprzedni” do utworu z kosza: użytkownik chce go posłuchać. Kliknięcie jest ważne przez
        // BACK_WINDOW_MS i tylko dla najbliższej zmiany utworu, więc cofnięcie na początek tego samego utworu nie
        // wyłącza pomijania na później.
        const wentBack = Date.now() - backPressedAt < BACK_WINDOW_MS;
        backPressedAt = 0;

        const item = Spicetify.Player.data?.item;
        if (wentBack || !item || !isTrashed(item)) {
            skipRun++;
            passed.clear();
            return;
        }
        skipTrashed(item, newContext).catch(console.error);
    }

    async function skipTrashed(item, newContext = false) {
        const run = ++skipRun;
        if (passed.has(trackKey(item))) return stopPlayback();
        passed.add(trackKey(item));

        const early = upcomingAfter(item, Spicetify.Player.origin.getQueue())?.find((track) => !isTrashed(track));
        if (early) return skipTo(early);

        const upcoming = await currentUpcoming(item, newContext);
        if (run !== skipRun) return;
        if (!upcoming) return Spicetify.Player.next();

        const target = upcoming.find((track) => !isTrashed(track));
        if (target) return skipTo(target);
        if (!upcoming.length) return Spicetify.Player.getRepeat() === 0 ? stopPlayback() : Spicetify.Player.next();

        // Ostatniego nie oznaczamy, bo na nim wylądujemy
        upcoming.slice(0, -1).forEach((track) => passed.add(trackKey(track)));
        return skipTo(upcoming[upcoming.length - 1]);
    }

    const skipTo = (track) => Spicetify.Player.origin.skipToNext({uri: track.uri, uid: track.uid});

    // Utwory, które według kolejki są za item; null, jeśli kolejka item nie zawiera
    function upcomingAfter(item, queue) {
        const upcoming = [...queue.queued, ...queue.nextUp];
        if (queue.current && trackKey(queue.current) === trackKey(item)) return upcoming;
        const index = upcoming.findIndex((track) => trackKey(track) === trackKey(item));
        return index === -1 ? null : upcoming.slice(index + 1);
    }

    // Utwory za item według kolejki, która ma go już jako bieżący; null, jeśli kolejka nie nadąży.
    //
    // Czekamy na zdarzenie queue_update, a nie odpytujemy, bo Spotify w tle ma przyhamowane timery. Pusta kolejka
    // z getQueue() bywa chwilowa (zaraz po zmianie utworu), ale zdarzenie z pustą klient wysyła dopiero, gdy przez 500 ms
    // nic jej nie zastąpi, więc to ono potwierdza koniec. Wyjątkiem jest świeżo puszczony kontekst: klient wczytuje
    // go dłużej i zdąży ogłosić pustą kolejkę, zanim dojdzie reszta, więc tam koniec potwierdza dopiero QUEUE_WAIT_MS.
    function currentUpcoming(item, newContext) {
        const api = Spicetify.Player.origin;
        const events = api.getEvents();
        const upcomingOf = (queue) =>
            queue?.current && trackKey(queue.current) === trackKey(item) ? [...queue.queued, ...queue.nextUp] : null;

        return new Promise((resolve) => {
            const done = (upcoming) => {
                clearTimeout(timeout);
                events.removeListener("queue_update", onUpdate);
                resolve(upcoming);
            };
            const onUpdate = (event) => {
                const upcoming = upcomingOf(event.data);
                if (upcoming && (upcoming.length || !newContext)) done(upcoming);
            };
            // Zdarzenie mogło przyjść, zanim zaczęliśmy słuchać; kolejka, która przez QUEUE_WAIT_MS się nie zmieniła,
            // też jest pewna
            const timeout = setTimeout(() => done(upcomingOf(api.getQueue())), QUEUE_WAIT_MS);
            events.addListener("queue_update", onUpdate);

            const upcoming = upcomingOf(api.getQueue());
            if (upcoming?.length) done(upcoming);
        });
    }

    function stopPlayback() {
        passed.clear();
        Spicetify.Player.pause();
        Spicetify.showNotification("Dalej są już tylko utwory z kosza");
    }

    // Nasłuch na całym dokumencie, bo Spotify potrafi przerenderować pasek odtwarzacza razem z przyciskiem
    function onDocumentClick(event) {
        if (event.target.closest?.(SKIP_BACK_SELECTOR)) backPressedAt = Date.now();
    }

    //------------------------Kopia w Augustusie (aplikacja na 127.0.0.1:8080, schemat spotify)-------------------------
    //
    // Źródłem prawdy jest LocalStorage: kosz musi działać od razu, także gdy aplikacja nie jest uruchomiona.
    // Augustus dostaje URI całego kosza po każdym zapisie i przy starcie, a sam tylko dopisuje - LocalStorage
    // wyczyszczony na przykład reinstalacją Spotify nie wyczyści kopii. Kasuje w niej wyłącznie wyjęcie z kosza; takie,
    // które nie doszło, czeka w LocalStorage i idzie przed następną synchronizacją.
    //
    // Opisy (tytuł, album, wykonawcy, nazwa wykonawcy z kosza) Trashbin dosyła do URI, których Augustus jeszcze nie
    // opisał. Pobiera je tymi samymi zapytaniami GraphQL, których używa sam Spotify: decorateContextTracks (lista URI
    // utworów) i getArtistNameAndTracks (URI wykonawcy).

    const DESCRIBE_BATCH = 50;
    const DESCRIBE_PAUSE_MS = 200; // przerwa między zapytaniami, żeby nie zasypać Spotify, kiedy do opisania jest cały kosz
    const unavailable = new Set(); // URI, których Spotify nie zna (np. utwór usunięty z serwisu) - do restartu nie pytamy ponownie
    let syncing = null;
    let syncAgain = false;

    async function postToAugustus(path, body) {
        const response = await fetch(`${AUGUSTUS}${path}`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(body),
        });
        if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
        return response.json();
    }

    // Jedna synchronizacja naraz; zmiana w trakcie dokłada jeszcze jeden przebieg po bieżącym
    function syncToAugustus() {
        if (syncing) {
            syncAgain = true;
            return syncing;
        }
        syncing = (async () => {
            do {
                syncAgain = false;
                await flushRemovals();
                const {added, missingTracks, missingArtists} = await postToAugustus("/sync", {
                    tracks: Object.keys(trash.track),
                    artists: Object.keys(trash.artist),
                });
                if (added) console.log(`Trashbin: nowe pozycje w Augustusie: ${added}`);
                await describeForAugustus(missingTracks, missingArtists);
            } while (syncAgain);
        })()
            .catch((error) => console.warn("Trashbin: Augustus nie odpowiada, kopia dogoni kosz przy następnej zmianie albo starcie", error))
            .finally(() => (syncing = null));
        return syncing;
    }

    // Paczkami po DESCRIBE_BATCH, każda zapisana od razu - przerwany przebieg (przy pustej kopii to cały kosz) idzie
    // przy następnym od miejsca, w którym stanął
    async function describeForAugustus(missingTracks, missingArtists) {
        const graphQL = Spicetify.GraphQL;
        const {decorateContextTracks, getArtistNameAndTracks} = graphQL?.Definitions ?? {};
        if (!graphQL?.Request || !decorateContextTracks || !getArtistNameAndTracks) {
            console.warn("Trashbin: brak zapytań GraphQL, Augustus nie dostanie nazw");
            return;
        }

        const tracks = missingTracks.filter((uri) => !unavailable.has(uri));
        const artists = missingArtists.filter((uri) => !unavailable.has(uri));
        const long = tracks.length + artists.length >= DESCRIBE_BATCH;
        if (long) Spicetify.showNotification(`Trashbin: do opisania dla Augustusa: ${tracks.length + artists.length}…`);
        let described = 0;

        for (let start = 0; start < tracks.length; start += DESCRIBE_BATCH) {
            const batch = tracks.slice(start, start + DESCRIBE_BATCH);
            const response = await graphQL.Request(decorateContextTracks, {uris: batch});
            const found = (response?.data ?? response)?.tracks;
            if (!found?.length) throw new Error(JSON.stringify(response?.errors ?? response));
            const descriptions = [];
            batch.forEach((uri, i) => {
                const track = found.length === batch.length ? found[i] : found.find((t) => t?.uri === uri);
                const description = track?.__typename === "Track" && describeTrack(uri, track);
                if (description) descriptions.push(description);
                else unavailable.add(uri);
            });
            if (descriptions.length) described += (await postToAugustus("/describe", {
                tracks: descriptions,
                artists: []
            })).described;
            await sleep(DESCRIBE_PAUSE_MS);
        }

        for (const uri of artists) {
            const response = await graphQL.Request(getArtistNameAndTracks, {uri, offset: 0, limit: 1});
            const artistUnion = (response?.data ?? response)?.artistUnion;
            if (!artistUnion) throw new Error(JSON.stringify(response?.errors ?? response));
            const name = artistUnion.profile?.name;
            if (!name) unavailable.add(uri);
            else described += (await postToAugustus("/describe", {tracks: [], artists: [{uri, name}]})).described;
            await sleep(DESCRIBE_PAUSE_MS);
        }

        if (long) Spicetify.showNotification(`Trashbin: opisy wysłane do Augustusa: ${described}`);
    }

    // Kształt odpowiedzi taki, jak mapuje go sam klient Spotify (artists.items[].uri + profile.name, albumOfTrack)
    function describeTrack(uri, track) {
        if (!track.name) return null;
        const artists = (track.artists?.items ?? [])
            .filter((artist) => artist?.uri && artist.profile?.name)
            .map((artist) => ({uri: artist.uri, name: artist.profile.name}));
        const album = track.albumOfTrack?.uri && track.albumOfTrack.name ? {
            uri: track.albumOfTrack.uri,
            name: track.albumOfTrack.name
        } : null;
        return {uri, title: track.name, album, artists};
    }

    async function flushRemovals() {
        if (!pendingRemovals.length) return;
        const uris = [...pendingRemovals];
        await postToAugustus("/remove", {uris});
        // W trakcie mogły dojść kolejne - zdejmujemy tylko wysłane
        pendingRemovals = pendingRemovals.filter((uri) => !uris.includes(uri));
        savePendingRemovals();
    }

    function queueRemoval(uri) {
        if (!pendingRemovals.includes(uri)) pendingRemovals.push(uri);
        savePendingRemovals();
    }

    // Wrzucony z powrotem, zanim wyjęcie doszło do Augustusa - wyjęcie już nieaktualne
    function cancelRemoval(uri) {
        if (!pendingRemovals.includes(uri)) return;
        pendingRemovals = pendingRemovals.filter((pending) => pending !== uri);
        savePendingRemovals();
    }

    function savePendingRemovals() {
        try {
            Spicetify.LocalStorage.set(PENDING_REMOVALS_KEY, JSON.stringify(pendingRemovals));
        } catch (error) {
            console.error(error);
        }
    }

    // Dokłada z Augustusa to, czego brakuje w LocalStorage, i pomija wyjęte, które jeszcze tam nie doszły
    async function restoreFromAugustus() {
        try {
            const response = await fetch(AUGUSTUS);
            if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
            const {tracks, artists} = await response.json();
            let restored = 0;
            for (const [list, uris] of [[trash.track, tracks], [trash.artist, artists]]) {
                for (const uri of uris) {
                    if (list[uri] || pendingRemovals.includes(uri)) continue;
                    list[uri] = true;
                    restored++;
                }
            }
            storeTrash();
            Spicetify.showNotification(`Przywrócone z Augustusa: ${restored}`);
        } catch (error) {
            Spicetify.showNotification("Nie udało się przywrócić - czy Augustus jest uruchomiony?", true);
            console.error(error);
        }
    }

    //-------------------------------------------------Menu kontekstowe-------------------------------------------------

    // Pozycja w menu tylko dla jednego utworu albo wykonawcy, z napisem zależnym od tego, czy już jest w koszu
    const menuItem = new Spicetify.ContextMenu.Item(
        ADD_LABEL,
        ([uri]) => toggle(uri),
        (uris) => {
            const list = uris.length === 1 ? listOf(uris[0]) : null;
            if (list) menuItem.name = list[uris[0]] ? REMOVE_LABEL : ADD_LABEL;
            return !!list;
        },
        TRASH_ICON
    );

    //---------------------------Przycisk kosza w wierszach tracklisty i na pasku odtwarzacza---------------------------
    //
    // Spotify przy aktualizacjach zmienia zahashowane klasy CSS, a css-map Spicetify nie zawsze nadąża (bez wpisu dla
    // paska Spicetify.Playbar.Widget nie ma się do czego przyczepić), dlatego oba miejsca znajdujemy po atrybutach:
    // - wiersz: [role="row"] -> ostatnia komórka [role="gridcell"] = sekcja z serduszkiem, czasem trwania i "..."
    //   (klasę .main-trackList-rowSectionEnd bierzemy, gdy css-map ją zna);
    // - pasek: now-playing-widget(-v2) -> przycisk dodawania do playlisty (buttonTertiary z aria-checked).

    const NOW_PLAYING_SELECTOR = '[data-testid="now-playing-widget"], [data-testid="now-playing-widget-v2"]';
    const PLAYBAR_ANCHOR_SELECTOR = 'button[data-encore-id="buttonTertiary"][aria-checked]';

    const buttonStyle = document.createElement("style");
    buttonStyle.textContent = `
        .trashbin-button { color: var(--text-subdued) !important; opacity: .7 !important; }
        .trashbin-button:hover { opacity: 1 !important; }
        .trashbin-button.trashbin-active { color: var(--text-negative) !important; opacity: 1 !important; visibility: visible !important; }
        .trashbin-in-row > .trashbin-button { margin-right: 12px !important; }
    `;
    document.head.appendChild(buttonStyle);

    const TrashButton = React.memo(function TrashButton({uri, className, iconClassName}) {
        const [trashed, setTrashed] = React.useState(() => !!trash.track[uri]);
        const ref = React.useRef(null);
        const label = trashed ? REMOVE_LABEL : ADD_LABEL;

        React.useEffect(() => {
            const update = () => setTrashed(!!trash.track[uri]);
            update();
            document.addEventListener(CHANGE_EVENT, update);
            return () => document.removeEventListener(CHANGE_EVENT, update);
        }, [uri]);

        React.useEffect(() => {
            if (!ref.current || typeof Spicetify.Tippy !== "function") return;
            const tooltip = Spicetify.Tippy(ref.current, {...Spicetify.TippyProps, hideOnClick: true, content: label});
            return () => tooltip.destroy();
        }, [label]);

        return React.createElement(
            "button",
            {
                ref,
                className: `${className} trashbin-button${trashed ? " trashbin-active" : ""}`,
                "aria-label": label,
                tabIndex: -1,
                onClick: (event) => {
                    event.stopPropagation();
                    toggle(uri);
                },
            },
            React.createElement("span", {
                className: iconClassName,
                "aria-hidden": "true",
                dangerouslySetInnerHTML: {__html: TRASH_ICON}
            })
        );
    });

    // Nowy wrapper z przyciskiem przed `before`. Klasy kopiujemy z natywnego przycisku `template`, żeby wyglądał jak on.
    function mountButton(parent, before, template, uri, placeClass) {
        const wrapper = document.createElement("div");
        wrapper.className = `trashbin-wrapper ${placeClass}`;
        wrapper.style.display = "contents";
        parent.insertBefore(wrapper, before);
        if (typeof ReactDOM.createRoot === "function") wrapper._trashbinRoot = ReactDOM.createRoot(wrapper);
        renderButton(wrapper, template, uri);
    }

    function renderButton(wrapper, template, uri) {
        wrapper.dataset.uri = uri;
        const button = React.createElement(TrashButton, {
            uri,
            className: template.className,
            iconClassName: template.querySelector(":scope > span")?.className ?? "",
        });
        if (wrapper._trashbinRoot) wrapper._trashbinRoot.render(button);
        else ReactDOM.render(button, wrapper);
    }

    function unmountButton(wrapper) {
        if (wrapper._trashbinRoot) wrapper._trashbinRoot.unmount();
        else ReactDOM.unmountComponentAtNode?.(wrapper);
        wrapper._trashbinRoot = null;
        wrapper.remove();
    }

    // Przycisk na pasku dla grającego utworu, przed przyciskiem dodawania do playlisty
    function syncPlaybarButton() {
        const existing = document.querySelector(".trashbin-playbar");
        const uri = Spicetify.Player.data?.item?.uri;
        const anchor = document.querySelector(NOW_PLAYING_SELECTOR)?.querySelector(PLAYBAR_ANCHOR_SELECTOR);
        if (!showWidget || !isTrackUri(uri) || !anchor) {
            if (existing) unmountButton(existing);
            return;
        }
        if (existing?.nextElementSibling !== anchor) {
            if (existing) unmountButton(existing);
            mountButton(anchor.parentElement, anchor, anchor, uri, "trashbin-playbar");
        } else if (existing.dataset.uri !== uri) {
            renderButton(existing, anchor, uri);
        }
    }

    function getRowEndCell(row) {
        const mapped = row.querySelector(".main-trackList-rowSectionEnd");
        if (mapped) return mapped;

        const cells = row.querySelectorAll('[role="gridcell"]');
        if (cells.length < 2) return null; // np. wiersze Biblioteki w sidebarze mają jedną komórkę
        const last = cells[cells.length - 1];
        // Zwykły wiersz ma przycisk "...", a utwór wykluczony z albumu/playlisty ma tylko przełącznik "Pokaż na tym albumie"
        return last.querySelector(':scope > button[aria-haspopup="menu"], :scope > button[role="switch"]') ? last : null;
    }

    // Pierwszy klucz "uri" w zagnieżdżonym obiekcie (max 10 poziomów, z ochroną przed cyklami)
    function findFirstUri(object, depth = 10, seen = new Set()) {
        if (depth <= 0 || !object || typeof object !== "object" || seen.has(object)) return undefined;
        seen.add(object);
        if (object.uri !== undefined) return object.uri;
        for (const key in object) {
            const found = findFirstUri(object[key], depth - 1, seen);
            if (found !== undefined) return found;
        }
        return undefined;
    }

    // Szuka URI utworu w propsach komponentu: props.uri, props.x.uri albo props.x.y.uri
    function trackUriFromProps(props) {
        const skip = (key) => key === "children" || key === "ref" || key.startsWith("on");
        const isPlainObject = (value) => value && typeof value === "object" && !Array.isArray(value);

        if (isTrackUri(props.uri)) return props.uri;
        for (const [key, value] of Object.entries(props)) {
            if (skip(key) || !isPlainObject(value)) continue;
            if (isTrackUri(value.uri)) return value.uri;
            for (const [key2, value2] of Object.entries(value)) {
                if (!skip(key2) && isPlainObject(value2) && isTrackUri(value2.uri)) return value2.uri;
            }
        }
        return null;
    }

    // DOM nie zawiera URI utworu, więc wyciągamy go z wewnętrznych danych Reacta podpiętych pod elementy wiersza
    function findTrackUri(row) {
        const candidates = [row.querySelector('[role="presentation"]'), row.querySelector('[role="gridcell"] button'), row].filter(Boolean);
        const reactKey = (element, prefix) => Object.keys(element).find((key) => key.startsWith(prefix));

        // 1) propsy przypięte bezpośrednio do elementu DOM
        for (const element of candidates) {
            const key = reactKey(element, "__reactProps$");
            const uri = key && findFirstUri(element[key]);
            if (isTrackUri(uri)) return uri;
        }

        // 2) drzewo komponentów (fiber) - idziemy w górę do 20 poziomów
        for (const element of candidates) {
            const key = reactKey(element, "__reactFiber$");
            for (let fiber = key && element[key], i = 0; fiber && i < 20; fiber = fiber.return, i++) {
                const props = fiber.memoizedProps || fiber.pendingProps;
                const uri = props && typeof props === "object" && trackUriFromProps(props);
                if (uri) return uri;
            }
        }
        return null;
    }

    function updateRow(row) {
        const cell = getRowEndCell(row);
        if (!cell) return;

        const existing = cell.querySelector(":scope > .trashbin-in-row");
        const uri = findTrackUri(row);
        if (existing && existing.dataset.uri === uri) return;

        // Wirtualizowana lista potrafi podmienić utwór w tym samym wierszu - wtedy montujemy od nowa
        if (existing) unmountButton(existing);
        if (!uri) return;

        // Wstawiamy przed serduszkiem (pomijając przełącznik "Ukryj na tej playliście" i ostatni przycisk "...").
        // Utwór wykluczony z albumu/playlisty nie ma serduszka - wtedy kosz idzie tuż za przełącznik, czyli w to samo miejsce.
        const heart = cell.querySelector(":scope > button:not([role='switch']):not(:last-child)");
        const hideSwitch = cell.querySelector(":scope > button[role='switch']");
        const template = heart ?? hideSwitch;
        if (!template) return;

        mountButton(cell, heart ?? hideSwitch.nextSibling, template, uri, "trashbin-in-row");
    }

    function startButtons() {
        const rowObserver = new MutationObserver((mutations) => {
            const rows = new Set();
            const removedWrappers = [];

            for (const mutation of mutations) {
                for (const node of mutation.removedNodes) {
                    if (node.nodeType !== Node.ELEMENT_NODE) continue;
                    if (node.matches(".trashbin-wrapper")) removedWrappers.push(node);
                    else removedWrappers.push(...node.querySelectorAll(".trashbin-wrapper"));
                }

                for (const node of mutation.addedNodes) {
                    // Pomijamy własne zmiany (montowanie naszych przycisków)
                    if (node.nodeType !== Node.ELEMENT_NODE || node.closest(".trashbin-wrapper")) continue;

                    if (node.matches('[role="row"]')) {
                        rows.add(node);
                        continue;
                    }
                    const nested = node.querySelectorAll('[role="row"]');
                    if (nested.length) nested.forEach((row) => rows.add(row));
                    else {
                        // Treść doładowana do istniejącego wiersza
                        const parentRow = node.closest('[role="row"]');
                        if (parentRow) rows.add(parentRow);
                    }
                }
            }

            // Wiersze, które Spotify wyrzuciło z DOM (np. przewijanie długiej playlisty) - sprzątamy ich roota Reacta.
            // isConnected, bo przeniesiony wiersz też daje "removed", a wtedy przycisk ma zostać.
            for (const wrapper of removedWrappers) {
                if (!wrapper.isConnected && wrapper._trashbinRoot) unmountButton(wrapper);
            }
            rows.forEach(updateRow);
            // Pasek też potrafi się przerenderować razem z przyciskiem, do którego się przyczepiamy
            syncPlaybarButton();
        });

        rowObserver.observe(document.body, {childList: true, subtree: true});
        document.querySelectorAll('[role="row"]').forEach(updateRow);
        syncPlaybarButton();
    }

    //-----------------------------------------Ustawienia (menu profilu → Kosz)-----------------------------------------

    const SETTINGS_STYLE = `
        .augustus-trashbin { display: grid; gap: 14px; min-inline-size: 360px; }
        .augustus-trashbin label { align-items: center; cursor: pointer; display: flex; gap: 12px; }
        .augustus-trashbin input { accent-color: var(--spice-button); block-size: 18px; inline-size: 18px; margin: 0; }
        .augustus-trashbin p { color: var(--spice-subtext); margin: 0; }
        .augustus-trashbin button {
            background: none; border: 1px solid var(--spice-subtext); border-radius: 999px; color: var(--spice-text);
            cursor: pointer; font-weight: 700; justify-self: start; padding: 6px 16px;
        }
        .augustus-trashbin button:hover { border-color: var(--spice-text); }
    `;

    const trashSummary = () =>
        `Utworów w koszu: ${Object.keys(trash.track).length}, wykonawców: ${Object.keys(trash.artist).length}.`;

    function openSettings() {
        const view = document.createElement("div");
        view.className = "augustus-trashbin";
        view.innerHTML = `
            <style>${SETTINGS_STYLE}</style>
            <label><input type="checkbox" name="widget">Przycisk kosza w pasku odtwarzacza</label>
            <p class="summary"></p>
            <button type="button" name="restore">Przywróć z Augustusa</button>
            <p>Dokłada wszystko, co Augustus ma, a czego brakuje w tym koszu.</p>
        `;

        const widget = view.querySelector('input[name="widget"]');
        widget.checked = showWidget;
        widget.onchange = () => {
            showWidget = widget.checked;
            Spicetify.LocalStorage.set(WIDGET_KEY, showWidget);
            syncPlaybarButton();
        };

        const summary = view.querySelector(".summary");
        summary.textContent = trashSummary();
        view.querySelector('button[name="restore"]').onclick = async () => {
            await restoreFromAugustus();
            summary.textContent = trashSummary();
        };

        Spicetify.PopupModal.display({title: "Kosz", content: view});
    }

    //----------------------Start (na samym końcu, żeby wszystkie const były już zainicjalizowane)----------------------

    new Spicetify.Menu.Item("Kosz", false, openSettings, TRASH_ICON).register();
    menuItem.register();
    document.addEventListener("click", onDocumentClick, true);
    Spicetify.Player.addEventListener("songchange", onSongChange);
    startButtons();
    onSongChange();

    // Synchronizacja z Augustusem z opóźnieniem, żeby nie spowalniać ładowania Spotify (opisy idą zapytaniami GraphQL)
    setTimeout(syncToAugustus, 5000);
})();
