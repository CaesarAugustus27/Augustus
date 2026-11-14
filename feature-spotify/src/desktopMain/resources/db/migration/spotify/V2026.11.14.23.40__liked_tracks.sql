-- Polubione utwory obok kosza. Opisy utworów przechodzą do wspólnej tabeli tracks, a kosz i polubione to osobne
-- tabele z datą na jej uri. artists trzyma teraz także wykonawców polubionych utworów.

-- Utwory z kosza albo z polubionych. title i album_uri są null, dopóki opis nie przyszedł, i zostają null, gdy
-- Spotify utworu już nie zna. Utwór spoza kosza i polubionych znika razem z wykonawcami w track_artists.
CREATE TABLE spotify.tracks
(
    uri       text NOT NULL,
    title     text,
    album_uri text,
    CONSTRAINT tracks_pkey PRIMARY KEY (uri),
    CONSTRAINT tracks_uri_check CHECK (uri ~ '^spotify:track:[0-9A-Za-z]+$'),
    CONSTRAINT tracks_album_fkey FOREIGN KEY (album_uri) REFERENCES spotify.albums (uri)
);

INSERT INTO spotify.tracks (uri, title, album_uri)
SELECT uri, title, album_uri
FROM spotify.trashed_tracks;

ALTER TABLE spotify.track_artists
    DROP CONSTRAINT track_artists_track_fkey,
    ADD CONSTRAINT track_artists_track_fkey FOREIGN KEY (track_uri) REFERENCES spotify.tracks (uri) ON DELETE CASCADE;

-- Utwory w koszu. trashed_at to pierwsze dotarcie do tej tabeli.
ALTER TABLE spotify.trashed_tracks
    DROP CONSTRAINT trashed_tracks_uri_check,
    DROP CONSTRAINT trashed_tracks_album_fkey,
    DROP COLUMN title,
    DROP COLUMN album_uri,
    ADD CONSTRAINT trashed_tracks_track_fkey FOREIGN KEY (uri) REFERENCES spotify.tracks (uri);

-- Lustro listy „Polubione utwory” ze Spotify: odlajkowany utwór znika. liked_at to data polubienia ze Spotify.
CREATE TABLE spotify.liked_tracks
(
    uri      text                     NOT NULL,
    liked_at timestamp with time zone NOT NULL,
    CONSTRAINT liked_tracks_pkey PRIMARY KEY (uri),
    CONSTRAINT liked_tracks_track_fkey FOREIGN KEY (uri) REFERENCES spotify.tracks (uri)
);
