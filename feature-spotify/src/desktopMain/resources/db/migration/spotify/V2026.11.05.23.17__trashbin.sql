-- Kopia kosza Trashbina ze Spicetify. Źródłem prawdy jest LocalStorage klienta Spotify, które trzyma same URI,
-- bo Trashbin musi pomijać utwory od razu, bez pytania aplikacji. Opisy są tylko tutaj: Trashbin pobiera je od
-- Spotify (tym samym zapytaniem GraphQL co klient) i dosyła do URI, których baza jeszcze nie opisała.

-- Wykonawcy wrzuceni do kosza i wykonawcy utworów z kosza. trashed_at = null: wykonawca nie jest w koszu, tylko
-- występuje w utworze z kosza. name = null tylko u wykonawcy z kosza, którego nazwa jeszcze nie przyszła.
CREATE TABLE spotify.artists
(
    uri        text NOT NULL,
    name       text,
    trashed_at timestamp with time zone,
    CONSTRAINT artists_pkey PRIMARY KEY (uri),
    CONSTRAINT artists_uri_check CHECK (uri ~ '^spotify:artist:[0-9A-Za-z]+$')
);

CREATE TABLE spotify.albums
(
    uri  text NOT NULL,
    name text NOT NULL,
    CONSTRAINT albums_pkey PRIMARY KEY (uri),
    CONSTRAINT albums_uri_check CHECK (uri ~ '^spotify:album:[0-9A-Za-z]+$')
);

-- Utwory w koszu. title i album_uri są null, dopóki opis nie przyszedł, i zostają null, gdy Spotify utworu już
-- nie zna. trashed_at to pierwsze dotarcie do tej tabeli
CREATE TABLE spotify.trashed_tracks
(
    uri        text                     NOT NULL,
    title      text,
    album_uri  text,
    trashed_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT trashed_tracks_pkey PRIMARY KEY (uri),
    CONSTRAINT trashed_tracks_uri_check CHECK (uri ~ '^spotify:track:[0-9A-Za-z]+$'),
    CONSTRAINT trashed_tracks_album_fkey FOREIGN KEY (album_uri) REFERENCES spotify.albums (uri)
);

-- Wykonawcy utworu w kolejności ze Spotify, od 0.
CREATE TABLE spotify.track_artists
(
    track_uri  text    NOT NULL,
    position   integer NOT NULL,
    artist_uri text    NOT NULL,
    CONSTRAINT track_artists_pkey PRIMARY KEY (track_uri, position),
    CONSTRAINT track_artists_track_fkey FOREIGN KEY (track_uri) REFERENCES spotify.trashed_tracks (uri) ON DELETE CASCADE,
    CONSTRAINT track_artists_artist_fkey FOREIGN KEY (artist_uri) REFERENCES spotify.artists (uri)
);

CREATE INDEX track_artists_artist_idx ON spotify.track_artists (artist_uri);
