package org.fordes.subtitles.view.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.util.StrUtil;
import javafx.scene.control.IndexRange;
import lombok.extern.slf4j.Slf4j;
import org.fordes.subtitles.view.enums.FileEnum;
import org.fordes.subtitles.view.handler.WriteFileHandler;
import org.fordes.subtitles.view.model.DTO.SearchDTO;
import org.fordes.subtitles.view.model.DTO.Subtitle;
import org.fordes.subtitles.view.utils.submerge.parser.ParserFactory;
import org.fordes.subtitles.view.utils.submerge.parser.SubtitleParser;
import org.fordes.subtitles.view.utils.submerge.subtitle.common.TimedLine;
import org.fordes.subtitles.view.utils.submerge.subtitle.common.TimedObject;
import org.fordes.subtitles.view.utils.submerge.subtitle.common.TimedTextFile;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author fordes on 2022/7/19
 */
@Slf4j
public class SubtitleUtil {

    /**
     * 简易文本搜索
     * @param content 被搜索文本
     * @param target 目标关键字
     * @param isIgnoreCase 忽略大小写
     * @param isRegular 正则搜索
     * @return 结果
     */
    public static SearchDTO search(String content, String target, boolean isIgnoreCase, boolean isRegular) {
        int cursor = 0;
        for (String line : content.split(StrUtil.LF)) {
            if (isRegular){
                Matcher matcher = Pattern.compile(target).matcher(line);
                if (matcher.find()) {
                    return new SearchDTO().setCursor_start(cursor +line.indexOf(matcher.group(0)))
                            .setCursor_end(cursor +line.indexOf(matcher.group(0))+ matcher.group(0).length()).setSuccess(true);
                }
            }else {
                int pos = StrUtil.indexOf(line, target, 0, isIgnoreCase);
                if (pos >= 0) {
                    return new SearchDTO().setCursor_start(cursor+ pos)
                            .setCursor_end(cursor+ pos+ target.length()).setSuccess(true);
                }
            }
            cursor +=line.length() +1;
        }
        return new SearchDTO();
    }

    /**
     * 文本替换
     * @param content 被处理内容
     * @param searchStr 被替换内容
     * @param replaceStr 替换内容
     * @param isAll 是否替换全部
     * @param isIgnoreCase 是否忽略大小写
     * @param isRegular （searchStr）是否为正则表达式
     * @return 结果
     */
    public static SearchDTO replace(String content, String searchStr, String replaceStr, boolean isAll, boolean isIgnoreCase, boolean isRegular) {
        if (isAll) {
            if (isRegular) {
                Matcher matcher = Pattern.compile(searchStr).matcher(content);
                if (matcher.find()) {
                    return new SearchDTO().setSuccess(true).setContent(matcher.replaceAll(replaceStr));
                }
            }else {
                return new SearchDTO().setSuccess(true).setContent(isIgnoreCase?
                        StrUtil.replaceIgnoreCase(content, searchStr, replaceStr):
                        StrUtil.replace(content, searchStr, replaceStr));
            }
            return new SearchDTO();
        }else {
            int cursor = 0;
            StringBuilder temp = new StringBuilder();
            SearchDTO result = new SearchDTO();
            for (String line : content.split(StrUtil.LF)) {
                if (isRegular){
                    Matcher matcher = Pattern.compile(searchStr).matcher(line);
                    if (!result.isSuccess() && matcher.find()) {
                        cursor +=line.indexOf(matcher.group(0));
                        result.setSuccess(true).setCursor_start(cursor).setCursor_end(cursor+ replaceStr.length());
                        temp.append(matcher.replaceFirst(replaceStr)).append(StrUtil.LF);
                        continue;
                    }
                }else {
                    int pos = StrUtil.indexOf(line, searchStr, 0, isIgnoreCase);
                    if (!result.isSuccess() && pos >= 0) {
                        line = line.substring(0, pos) + replaceStr + line.substring(pos+ searchStr.length());
                        result.setSuccess(true).setCursor_start(cursor+ pos).setCursor_end(cursor +pos+ replaceStr.length());
                        temp.append(line).append(StrUtil.LF);
                        continue;
                    }
                }
                temp.append(line).append(StrUtil.LF);
                cursor +=line.length() +1;
            }
            return result.setContent(temp.toString());
        }
    }


    /**
     * 时间轴位移
     * @param timedTextFile 字幕
     * @param begin 开始时间
     * @param range 位移范围
     * @param mode  显示模式
     * @return  时间轴位移后的字幕
     */
    public static TimedTextFile reviseTimeLine(TimedTextFile timedTextFile, LocalTime begin, IndexRange range, boolean mode) {
        LocalTime start = CollUtil.getFirst(timedTextFile.getTimedLines()).getTime().getStart();
        long poor = begin.toNanoOfDay() - start.toNanoOfDay();
        if (range != null) {
            long sort = 0;
            for (TimedLine item : timedTextFile.getTimedLines()) {
                sort += subtitleDisplay(item, mode).length();
                if (sort > range.getEnd()) {
                    break;
                } else if (sort >= range.getStart()) {
                    item.getTime().setStart(LocalTime.ofNanoOfDay(item.getTime().getStart().toNanoOfDay() + poor));
                    item.getTime().setEnd(LocalTime.ofNanoOfDay(item.getTime().getEnd().toNanoOfDay() + poor));
                }
            }
        } else {
            for (TimedLine item : timedTextFile.getTimedLines()) {
                reviseTimeLine(item.getTime(), poor);
            }
        }
        return timedTextFile;
    }

    private static void reviseTimeLine(TimedObject timedLine, long poor) {
        timedLine.setStart(LocalTime.ofNanoOfDay(timedLine.getStart().toNanoOfDay() + poor));
        timedLine.setEnd(LocalTime.ofNanoOfDay(timedLine.getEnd().toNanoOfDay() + poor));
    }

    public static TimedTextFile reviseTimeLine(TimedTextFile timedTextFile, LocalTime begin, boolean mode) {
        return reviseTimeLine(timedTextFile, begin, null, mode);
    }

    public static Subtitle editorChange(final Subtitle subtitle, List<String> lines, int row) {
        Set<? extends TimedLine> timedLines = subtitle.getTimedTextFile().getTimedLines();
        int sort = 0;
        for (int i = 0; i < timedLines.size(); i++) {
            TimedLine line = CollUtil.get(timedLines, i);
            sort += line.getTextLines().size();
            if (row <= sort) {
                line.getTextLines().set(sort - row, lines.get(row - 1));
                break;
            }
        }
        return subtitle;
    }


    public static void readSubtitleFile(Subtitle subtitle) throws Exception {
        TimeInterval timer = DateUtil.timer();
        SubtitleParser parser = ParserFactory.getParser(subtitle.getFormat().suffix);
        TimedTextFile content = parser.parse(subtitle.getFile(), subtitle.getCharset());
        log.debug("解析字幕耗时：{} ms", timer.interval());
        subtitle.setTimedTextFile(content);
    }

    public static TimedTextFile readSubtitleStr(String str, FileEnum type) throws Exception {
        return ParserFactory.getParser(type.suffix).parse(str, StrUtil.EMPTY);
    }

    /**
     * 字幕结构转换为字符串
     * @param mode 解析模式 f-简洁模式 t-完整模式
     * @return 字符串
     */
    public static String subtitleDisplay(TimedTextFile subtitle, boolean mode) {
        if (!mode) {
            StringBuilder content = new StringBuilder();
            subtitle.getTimedLines().forEach(item
                    -> content.append(CollUtil.join(item.getTextLines(), StrUtil.CRLF)).append(StrUtil.CRLF));
            return content.toString();
        }else {
            return subtitle.toString();
        }
    }
    /**
     * 字幕结构转换为字符串
     *
     * @param mode 解析模式 f-简洁模式 t-完整模式
     * @return 字符串
     */
    public static String subtitleDisplay(TimedLine timedLine, boolean mode) {
        return mode ? timedLine.toString() : CollUtil.join(timedLine.getTextLines(), StrUtil.CRLF);
    }

    /**
     * 写入字幕结构到源文件
     * @param subtitle  字幕
     * @param handler   回调
     */
    public static void writeSubtitleToFile(Subtitle subtitle, WriteFileHandler handler) {
        try {
            FileUtils.write(subtitle.getFile(), subtitle.getTimedTextFile().toString(), subtitle.getCharset());
        }catch (RuntimeException e) {
            handler.handle(false);
        }
        handler.handle(true);
    }
}
