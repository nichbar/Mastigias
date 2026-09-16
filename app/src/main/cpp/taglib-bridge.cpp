#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include <taglib/fileref.h>
#include <taglib/tag.h>
#include <taglib/tbytevector.h>
#include <taglib/tstring.h>
#include <taglib/tpropertymap.h>
#include <taglib/audioproperties.h>

// Audio Container format headers
#include <taglib/flacfile.h>
#include <taglib/flacpicture.h>
#include <taglib/mpegfile.h>
#include <taglib/id3v2tag.h>
#include <taglib/id3v2frame.h>
#include <taglib/attachedpictureframe.h>
#include <taglib/mp4file.h>
#include <taglib/mp4tag.h>
#include <taglib/mp4coverart.h>
#include <taglib/mp4item.h>
#include <taglib/vorbisfile.h>
#include <taglib/opusfile.h>
#include <taglib/xiphcomment.h>
#include <taglib/asffile.h>
#include <taglib/asftag.h>
#include <taglib/asfpicture.h>
#include <taglib/asfattribute.h>
#include <taglib/wavfile.h>
#include <taglib/aifffile.h>

#define TAG "MastigiasNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jobject JNICALL
Java_now_link_mastigias_data_taglib_TagLibBridge_nativeReadMetadata(
    JNIEnv* env, jobject /*thiz*/, jstring jFilePath) {

    if (!jFilePath) return nullptr;

    const char* filePathChars = env->GetStringUTFChars(jFilePath, nullptr);
    if (!filePathChars) return nullptr;

    std::string filePath(filePathChars);
    env->ReleaseStringUTFChars(jFilePath, filePathChars);

    TagLib::FileRef f(filePath.c_str());

    if (f.isNull() || !f.file()) {
        LOGE("nativeReadMetadata: Failed to open audio file: %s", filePath.c_str());
        return nullptr;
    }

    TagLib::PropertyMap tags = f.file()->properties();
    std::vector<std::string> keys;
    std::vector<std::string> values;

    for (const auto& entry : tags) {
        if (!entry.second.isEmpty()) {
            keys.push_back(entry.first.toCString(true));
            values.push_back(entry.second.front().toCString(true));
        }
    }

    int bitrate = 0;
    int sampleRate = 0;
    int channels = 0;
    jlong durationMs = 0;

    if (f.audioProperties()) {
        bitrate = f.audioProperties()->bitrate();
        sampleRate = f.audioProperties()->sampleRate();
        channels = f.audioProperties()->channels();
        durationMs = f.audioProperties()->lengthInMilliseconds();
    }

    // Construct String[] arrays for JNI transfer
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) {
        LOGE("nativeReadMetadata: Failed to find java/lang/String class");
        return nullptr;
    }

    jobjectArray jKeys = env->NewObjectArray(static_cast<jsize>(keys.size()), stringClass, nullptr);
    jobjectArray jValues = env->NewObjectArray(static_cast<jsize>(values.size()), stringClass, nullptr);

    for (size_t i = 0; i < keys.size(); ++i) {
        jstring kStr = env->NewStringUTF(keys[i].c_str());
        jstring vStr = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(jKeys, static_cast<jsize>(i), kStr);
        env->SetObjectArrayElement(jValues, static_cast<jsize>(i), vStr);
        env->DeleteLocalRef(kStr);
        env->DeleteLocalRef(vStr);
    }

    jclass bundleClass = env->FindClass("now/link/mastigias/data/taglib/NativeTagBundle");
    if (!bundleClass) {
        LOGE("nativeReadMetadata: Failed to find NativeTagBundle class");
        return nullptr;
    }

    // Attempt to locate (keys, values, bitrate, sampleRate, channels, durationMs: Long) constructor
    jmethodID bundleCtor = env->GetMethodID(
        bundleClass, "<init>", "([Ljava/lang/String;[Ljava/lang/String;IIIJ)V");

    if (!bundleCtor) {
        env->ExceptionClear();
        // Fallback for (durationMs: Int) constructor if present
        bundleCtor = env->GetMethodID(
            bundleClass, "<init>", "([Ljava/lang/String;[Ljava/lang/String;IIII)V");
        if (!bundleCtor) {
            LOGE("nativeReadMetadata: Failed to find NativeTagBundle constructor");
            return nullptr;
        }
        return env->NewObject(
            bundleClass, bundleCtor, jKeys, jValues, bitrate, sampleRate, channels, static_cast<jint>(durationMs));
    }

    return env->NewObject(
        bundleClass, bundleCtor, jKeys, jValues, bitrate, sampleRate, channels, durationMs);
}

JNIEXPORT jbyteArray JNICALL
Java_now_link_mastigias_data_taglib_TagLibBridge_nativeReadArtwork(
    JNIEnv* env, jobject /*thiz*/, jstring jFilePath) {

    if (!jFilePath) return nullptr;

    const char* filePathChars = env->GetStringUTFChars(jFilePath, nullptr);
    if (!filePathChars) return nullptr;

    std::string filePath(filePathChars);
    env->ReleaseStringUTFChars(jFilePath, filePathChars);

    TagLib::FileRef f(filePath.c_str());

    if (f.isNull() || !f.file()) {
        LOGE("nativeReadArtwork: Failed to open file: %s", filePath.c_str());
        return nullptr;
    }

    std::vector<char> artworkBytes;
    TagLib::File* file = f.file();

    // 1. FLAC
    if (auto* flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
        TagLib::List<TagLib::FLAC::Picture*> pictures = flacFile->pictureList();
        for (auto* pic : pictures) {
            if (pic && !pic->data().isEmpty()) {
                if (pic->type() == TagLib::FLAC::Picture::FrontCover || artworkBytes.empty()) {
                    artworkBytes.assign(pic->data().data(), pic->data().data() + pic->data().size());
                    if (pic->type() == TagLib::FLAC::Picture::FrontCover) break;
                }
            }
        }
    }
    // 2. MPEG (MP3)
    else if (auto* mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
        if (mpegFile->ID3v2Tag()) {
            const TagLib::ID3v2::FrameList& frames = mpegFile->ID3v2Tag()->frameList("APIC");
            for (auto* frame : frames) {
                auto* picFrame = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frame);
                if (picFrame && !picFrame->picture().isEmpty()) {
                    if (picFrame->type() == TagLib::ID3v2::AttachedPictureFrame::FrontCover || artworkBytes.empty()) {
                        artworkBytes.assign(picFrame->picture().data(), picFrame->picture().data() + picFrame->picture().size());
                        if (picFrame->type() == TagLib::ID3v2::AttachedPictureFrame::FrontCover) break;
                    }
                }
            }
        }
    }
    // 3. MP4 / M4A / AAC
    else if (auto* mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
        if (mp4File->tag()) {
            TagLib::MP4::ItemListMap& items = mp4File->tag()->itemListMap();
            if (items.contains("covr")) {
                TagLib::MP4::CoverArtList covers = items["covr"].toCoverArtList();
                if (!covers.isEmpty() && !covers.front().data().isEmpty()) {
                    artworkBytes.assign(covers.front().data().data(), covers.front().data().data() + covers.front().data().size());
                }
            }
        }
    }
    // 4. Ogg Vorbis
    else if (auto* vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
        if (vorbisFile->tag()) {
            TagLib::List<TagLib::FLAC::Picture*> pictures = vorbisFile->tag()->pictureList();
            for (auto* pic : pictures) {
                if (pic && !pic->data().isEmpty()) {
                    if (pic->type() == TagLib::FLAC::Picture::FrontCover || artworkBytes.empty()) {
                        artworkBytes.assign(pic->data().data(), pic->data().data() + pic->data().size());
                        if (pic->type() == TagLib::FLAC::Picture::FrontCover) break;
                    }
                }
            }
        }
    }
    // 5. Ogg Opus
    else if (auto* opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
        if (opusFile->tag()) {
            TagLib::List<TagLib::FLAC::Picture*> pictures = opusFile->tag()->pictureList();
            for (auto* pic : pictures) {
                if (pic && !pic->data().isEmpty()) {
                    if (pic->type() == TagLib::FLAC::Picture::FrontCover || artworkBytes.empty()) {
                        artworkBytes.assign(pic->data().data(), pic->data().data() + pic->data().size());
                        if (pic->type() == TagLib::FLAC::Picture::FrontCover) break;
                    }
                }
            }
        }
    }
    // 6. RIFF WAV
    else if (auto* wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
        if (wavFile->ID3v2Tag()) {
            const TagLib::ID3v2::FrameList& frames = wavFile->ID3v2Tag()->frameList("APIC");
            for (auto* frame : frames) {
                auto* picFrame = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frame);
                if (picFrame && !picFrame->picture().isEmpty()) {
                    artworkBytes.assign(picFrame->picture().data(), picFrame->picture().data() + picFrame->picture().size());
                    break;
                }
            }
        }
    }
    // 7. RIFF AIFF
    else if (auto* aiffFile = dynamic_cast<TagLib::RIFF::AIFF::File*>(file)) {
        if (auto* id3v2 = dynamic_cast<TagLib::ID3v2::Tag*>(aiffFile->tag())) {
            const TagLib::ID3v2::FrameList& frames = id3v2->frameList("APIC");
            for (auto* frame : frames) {
                auto* picFrame = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frame);
                if (picFrame && !picFrame->picture().isEmpty()) {
                    artworkBytes.assign(picFrame->picture().data(), picFrame->picture().data() + picFrame->picture().size());
                    break;
                }
            }
        }
    }
    // 8. ASF / WMA
    else if (auto* asfFile = dynamic_cast<TagLib::ASF::File*>(file)) {
        if (asfFile->tag()) {
            const TagLib::ASF::AttributeListMap& attrMap = asfFile->tag()->attributeListMap();
            if (attrMap.contains("WM/Picture")) {
                for (const auto& attr : attrMap["WM/Picture"]) {
                    TagLib::ASF::Picture pic = attr.toPicture();
                    if (pic.isValid() && !pic.picture().isEmpty()) {
                        artworkBytes.assign(pic.picture().data(), pic.picture().data() + pic.picture().size());
                        break;
                    }
                }
            }
        }
    }

    if (artworkBytes.empty()) {
        return nullptr;
    }

    jbyteArray jResult = env->NewByteArray(static_cast<jsize>(artworkBytes.size()));
    if (!jResult) return nullptr;

    env->SetByteArrayRegion(
        jResult, 0, static_cast<jsize>(artworkBytes.size()), reinterpret_cast<const jbyte*>(artworkBytes.data()));
    return jResult;
}

JNIEXPORT jboolean JNICALL
Java_now_link_mastigias_data_taglib_TagLibBridge_nativeWriteMetadata(
    JNIEnv* env, jobject /*thiz*/, jstring jFilePath,
    jobjectArray setKeys, jobjectArray setValues, jobjectArray deleteKeys,
    jbyteArray artworkBytes, jboolean removeArtwork,
    jstring artworkMime, jint artworkWidth, jint artworkHeight) {

    if (!jFilePath) return JNI_FALSE;

    const char* filePathChars = env->GetStringUTFChars(jFilePath, nullptr);
    if (!filePathChars) return JNI_FALSE;

    std::string filePath(filePathChars);
    env->ReleaseStringUTFChars(jFilePath, filePathChars);

    TagLib::FileRef f(filePath.c_str());
    if (f.isNull() || !f.file()) {
        LOGE("nativeWriteMetadata: Failed to open file: %s", filePath.c_str());
        return JNI_FALSE;
    }

    TagLib::PropertyMap propMap = f.file()->properties();

    // 1. Process deletions
    int delCount = deleteKeys ? env->GetArrayLength(deleteKeys) : 0;
    for (int i = 0; i < delCount; ++i) {
        auto jDelKey = static_cast<jstring>(env->GetObjectArrayElement(deleteKeys, i));
        if (jDelKey) {
            const char* delKey = env->GetStringUTFChars(jDelKey, nullptr);
            if (delKey) {
                propMap.erase(TagLib::String(delKey, TagLib::String::UTF8));
                env->ReleaseStringUTFChars(jDelKey, delKey);
            }
            env->DeleteLocalRef(jDelKey);
        }
    }

    // 2. Process updates
    int setCount = (setKeys && setValues) ? env->GetArrayLength(setKeys) : 0;
    for (int i = 0; i < setCount; ++i) {
        auto jKey = static_cast<jstring>(env->GetObjectArrayElement(setKeys, i));
        auto jVal = static_cast<jstring>(env->GetObjectArrayElement(setValues, i));
        if (jKey && jVal) {
            const char* key = env->GetStringUTFChars(jKey, nullptr);
            const char* val = env->GetStringUTFChars(jVal, nullptr);
            if (key && val) {
                propMap.replace(
                    TagLib::String(key, TagLib::String::UTF8),
                    TagLib::StringList(TagLib::String(val, TagLib::String::UTF8))
                );
            }
            if (key) env->ReleaseStringUTFChars(jKey, key);
            if (val) env->ReleaseStringUTFChars(jVal, val);
        }
        if (jKey) env->DeleteLocalRef(jKey);
        if (jVal) env->DeleteLocalRef(jVal);
    }

    f.file()->setProperties(propMap);

    // 3. Process Artwork across container formats
    TagLib::File* file = f.file();
    const char* mimeStrChars = artworkMime ? env->GetStringUTFChars(artworkMime, nullptr) : nullptr;
    const char* effectiveMime = mimeStrChars ? mimeStrChars : "image/jpeg";

    jbyte* rawArtBytes = nullptr;
    jsize artSize = 0;

    if (artworkBytes != nullptr && !removeArtwork) {
        artSize = env->GetArrayLength(artworkBytes);
        rawArtBytes = env->GetByteArrayElements(artworkBytes, nullptr);
    }

    if (removeArtwork == JNI_TRUE) {
        // --- REMOVE ARTWORK ---
        if (auto* flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
            flacFile->removePictures();
        } else if (auto* mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
            if (mpegFile->ID3v2Tag()) mpegFile->ID3v2Tag()->removeFrames("APIC");
        } else if (auto* mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
            if (mp4File->tag()) mp4File->tag()->itemListMap().erase("covr");
        } else if (auto* vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
            if (vorbisFile->tag()) {
                vorbisFile->tag()->removeAllPictures();
                vorbisFile->tag()->removeFields("COVERART");
                vorbisFile->tag()->removeFields("COVERARTMIME");
            }
        } else if (auto* opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
            if (opusFile->tag()) {
                opusFile->tag()->removeAllPictures();
                opusFile->tag()->removeFields("COVERART");
                opusFile->tag()->removeFields("COVERARTMIME");
            }
        } else if (auto* wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
            if (wavFile->ID3v2Tag()) wavFile->ID3v2Tag()->removeFrames("APIC");
        } else if (auto* aiffFile = dynamic_cast<TagLib::RIFF::AIFF::File*>(file)) {
            if (auto* id3v2 = dynamic_cast<TagLib::ID3v2::Tag*>(aiffFile->tag())) {
                id3v2->removeFrames("APIC");
            }
        } else if (auto* asfFile = dynamic_cast<TagLib::ASF::File*>(file)) {
            if (asfFile->tag()) asfFile->tag()->removeItem("WM/Picture");
        }
    } else if (rawArtBytes != nullptr && artSize > 0) {
        // --- ADD / REPLACE ARTWORK ---
        TagLib::ByteVector byteVector(reinterpret_cast<const char*>(rawArtBytes), static_cast<size_t>(artSize));

        // 1. FLAC
        if (auto* flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
            flacFile->removePictures();
            auto* pic = new TagLib::FLAC::Picture();
            pic->setData(byteVector);
            pic->setType(TagLib::FLAC::Picture::FrontCover);
            pic->setMimeType(TagLib::String(effectiveMime, TagLib::String::UTF8));
            pic->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
            pic->setWidth(artworkWidth > 0 ? artworkWidth : 1);
            pic->setHeight(artworkHeight > 0 ? artworkHeight : 1);
            pic->setColorDepth(24);
            flacFile->addPicture(pic);
        }
        // 2. MPEG (MP3)
        else if (auto* mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
            TagLib::ID3v2::Tag* id3v2 = mpegFile->ID3v2Tag(true);
            id3v2->removeFrames("APIC");
            auto* frame = new TagLib::ID3v2::AttachedPictureFrame();
            frame->setPicture(byteVector);
            frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
            frame->setMimeType(TagLib::String(effectiveMime, TagLib::String::UTF8));
            frame->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
            id3v2->addFrame(frame);
        }
        // 3. MP4 / M4A / AAC
        else if (auto* mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
            if (mp4File->tag()) {
                TagLib::MP4::CoverArt::Format fmt = (std::string(effectiveMime).find("png") != std::string::npos)
                    ? TagLib::MP4::CoverArt::PNG
                    : TagLib::MP4::CoverArt::JPEG;
                TagLib::MP4::CoverArt cover(fmt, byteVector);
                TagLib::MP4::CoverArtList coverList;
                coverList.append(cover);
                mp4File->tag()->itemListMap()["covr"] = TagLib::MP4::Item(coverList);
            }
        }
        // 4. Ogg Vorbis
        else if (auto* vorbisFile = dynamic_cast<TagLib::Ogg::Vorbis::File*>(file)) {
            if (vorbisFile->tag()) {
                vorbisFile->tag()->removeAllPictures();
                vorbisFile->tag()->removeFields("COVERART");
                vorbisFile->tag()->removeFields("COVERARTMIME");
                auto* pic = new TagLib::FLAC::Picture();
                pic->setData(byteVector);
                pic->setType(TagLib::FLAC::Picture::FrontCover);
                pic->setMimeType(TagLib::String(effectiveMime, TagLib::String::UTF8));
                pic->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                pic->setWidth(artworkWidth > 0 ? artworkWidth : 1);
                pic->setHeight(artworkHeight > 0 ? artworkHeight : 1);
                pic->setColorDepth(24);
                vorbisFile->tag()->addPicture(pic);
            }
        }
        // 5. Ogg Opus
        else if (auto* opusFile = dynamic_cast<TagLib::Ogg::Opus::File*>(file)) {
            if (opusFile->tag()) {
                opusFile->tag()->removeAllPictures();
                opusFile->tag()->removeFields("COVERART");
                opusFile->tag()->removeFields("COVERARTMIME");
                auto* pic = new TagLib::FLAC::Picture();
                pic->setData(byteVector);
                pic->setType(TagLib::FLAC::Picture::FrontCover);
                pic->setMimeType(TagLib::String(effectiveMime, TagLib::String::UTF8));
                pic->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                pic->setWidth(artworkWidth > 0 ? artworkWidth : 1);
                pic->setHeight(artworkHeight > 0 ? artworkHeight : 1);
                pic->setColorDepth(24);
                opusFile->tag()->addPicture(pic);
            }
        }
        // 6. RIFF WAV
        else if (auto* wavFile = dynamic_cast<TagLib::RIFF::WAV::File*>(file)) {
            TagLib::ID3v2::Tag* id3v2 = wavFile->ID3v2Tag();
            if (id3v2) {
                id3v2->removeFrames("APIC");
                auto* frame = new TagLib::ID3v2::AttachedPictureFrame();
                frame->setPicture(byteVector);
                frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
                frame->setMimeType(TagLib::String(effectiveMime, TagLib::String::UTF8));
                frame->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                id3v2->addFrame(frame);
            }
        }
        // 7. RIFF AIFF
        else if (auto* aiffFile = dynamic_cast<TagLib::RIFF::AIFF::File*>(file)) {
            if (auto* id3v2 = dynamic_cast<TagLib::ID3v2::Tag*>(aiffFile->tag())) {
                id3v2->removeFrames("APIC");
                auto* frame = new TagLib::ID3v2::AttachedPictureFrame();
                frame->setPicture(byteVector);
                frame->setType(TagLib::ID3v2::AttachedPictureFrame::FrontCover);
                frame->setMimeType(TagLib::String(effectiveMime, TagLib::String::UTF8));
                frame->setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                id3v2->addFrame(frame);
            }
        }
        // 8. ASF / WMA
        else if (auto* asfFile = dynamic_cast<TagLib::ASF::File*>(file)) {
            if (asfFile->tag()) {
                asfFile->tag()->removeItem("WM/Picture");
                TagLib::ASF::Picture pic;
                pic.setPicture(byteVector);
                pic.setType(TagLib::ASF::Picture::FrontCover);
                pic.setMimeType(TagLib::String(effectiveMime, TagLib::String::UTF8));
                pic.setDescription(TagLib::String("Front Cover", TagLib::String::UTF8));
                asfFile->tag()->addAttribute("WM/Picture", TagLib::ASF::Attribute(pic));
            }
        }
    }

    if (rawArtBytes != nullptr) {
        env->ReleaseByteArrayElements(artworkBytes, rawArtBytes, JNI_ABORT);
    }
    if (mimeStrChars != nullptr) {
        env->ReleaseStringUTFChars(artworkMime, mimeStrChars);
    }

    bool success = f.file()->save();
    return success ? JNI_TRUE : JNI_FALSE;
}

}
