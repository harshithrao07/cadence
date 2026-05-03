import { ArtistPreviewDTO } from "./Artists";
import { PlaylistPreviewDTO } from "./Playlist";

export interface UserPreviewDTO {
    id: string;
    name: string;
    profileUrl: string;
}

export interface UserProfileChangeDTO {
    name?: string;
}

export interface UserProfileDTO {
    id: string;
    name: string;
    email: string;
    profileUrl: string;
    createdPlaylistsPreview: PlaylistPreviewDTO[];
    likedPlaylistsPreview: PlaylistPreviewDTO[];
    artistFollowing: ArtistPreviewDTO[];
    emailVerified: boolean,
    isOwner: boolean
}
