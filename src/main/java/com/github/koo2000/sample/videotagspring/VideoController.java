package com.github.koo2000.sample.videotagspring;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.List;

@Controller
public class VideoController {

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @RequestMapping("/")
    public String welcome() {
        return "index";
    }

    @GetMapping("/upload")
    public String uploadIndex(Model model) {
        new UploadForm();
        return "upload";
    }

    @Data
    public static class UploadForm {
        private MultipartFile uploadFile;
    }

    @PostMapping("/upload")
    public String upload(UploadForm form, Model model) {
        System.out.println("file = " + form.getUploadFile().getOriginalFilename());
        model.addAttribute("updateSuccess", true);

        try {
            jdbcTemplate.update("insert into video_data(file_name, data) values(:fileName, :data)"
                    , new MapSqlParameterSource()
                            .addValue("fileName", form.getUploadFile().getOriginalFilename())
                            .addValue("data", form.getUploadFile().getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException("input stream create failed", e);
        }
        return "upload";
    }

    @Data
    static class VideoData {
        final private long id;
        final private String fileName;
    }

    @GetMapping("/video")
    public String video(Model model) {
        return video(null, model);
    }

    @GetMapping("/video/{videoId}")
    public String video(@PathVariable String videoId, Model model) {

        List<VideoData>
                fileList = jdbcTemplate.query("select rownum, video_id, file_name from " +
                        " (select rownum, video_id, file_name from video_data order by video_id) r where rownum <= 5",
                (resultSet, i) -> new VideoData(resultSet.getLong("VIDEO_ID"),
                        resultSet.getString("FILE_NAME")));

        model.addAttribute("fileList", fileList);

        if (videoId != null) {
            Integer count = jdbcTemplate.queryForObject("select count(*) from video_data where video_id = :video_id"
                    , new MapSqlParameterSource().addValue("video_id", videoId)
                    , Integer.class);

            if (count > 0) {
                model.addAttribute("displayVideoId", videoId);
            }
        }
        return "video";
    }

    // Range not support
    @GetMapping("/videoData/{videoId}")
    public ResponseEntity videoData(@PathVariable String videoId, Model model, @RequestHeader HttpHeaders requestHeaders) {

        File data;

        requestHeaders.getRange().forEach(httpRange -> System.out.println("range = " + httpRange));
        try {
            data = getVideoData(videoId);
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();

        FileSystemResource resource = new FileSystemResource(data);

        System.out.println("download = " + videoId);
        return ResponseEntity.ok().headers(headers).contentType(MediaType.valueOf("video/mp4"))
                .body(resource);
    }

    // support Range
    @GetMapping("/videoDataRange/{videoId}")
    public void videoDataRange(@PathVariable String videoId, HttpServletRequest request,
                               HttpServletResponse response) throws ServletException, IOException {
        try {
            request.setAttribute(FileResourceHttpRequestHandler.FILE_KEY, getVideoData(videoId));
        } catch (EmptyResultDataAccessException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        fileResourceHttpRequestHandler.handleRequest(request, response);
    }

    @Autowired
    private FileResourceHttpRequestHandler fileResourceHttpRequestHandler;

    @Component
    static class FileResourceHttpRequestHandler extends ResourceHttpRequestHandler {
        public static
        final String FILE_KEY = FileResourceHttpRequestHandler.class.getName() + ".file";

        @Override
        protected MediaType getMediaType(HttpServletRequest request, Resource resource) {
            return MediaType.valueOf("video/mp4");
        }

        @Override
        protected Resource getResource(HttpServletRequest request) {
            return new FileSystemResource((File) request.getAttribute(FILE_KEY));
        }

        @Override
        public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            try {
                super.handleRequest(request, response);
            } finally {
                deleteFile(request);
            }
        }

        private void deleteFile(HttpServletRequest request) {
            File file = (File) request.getAttribute(FILE_KEY);
            if (file != null && file.exists()) {
                file.delete();
            }
        }
    }

    private File getVideoData(@PathVariable String videoId) throws EmptyResultDataAccessException {
        File data;
        data = jdbcTemplate.queryForObject("select data from video_data where video_id = :video_id",
                new MapSqlParameterSource().addValue("video_id", videoId),
                (resultSet, i) -> BlobUtil.createTempFileAndCopy(resultSet.getBlob("DATA"), 0, -1));
        return data;
    }
}